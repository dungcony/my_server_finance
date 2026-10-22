package com.datn.financeapp.unitTest.category.service.impl;

import com.datn.financeapp.module.category.service.impl.*;


import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.dto.request.CategoryCreateReq;
import com.datn.financeapp.module.category.dto.request.CategoryUpdateReq;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.entity.Category;
import com.datn.financeapp.module.category.mapper.CateMapper;
import com.datn.financeapp.module.category.repository.CategoryRepository;
import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.service.IconService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CateBehavierImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private IconService iconService;

    @Mock
    private CateMapper cateMapper;

    private CateBehavierImpl behavierService;

    @BeforeEach
    void setUp() {
        behavierService = new CateBehavierImpl(categoryRepository, iconService, cateMapper);
    }

    @Test
    @DisplayName("create: code đã tồn tại ném CATEGORY_CODE_EXISTS")
    void create_duplicateCode_throwsException() {
        CategoryCreateReq req = new CategoryCreateReq("Ăn uống", "an_uong", "mô tả", "an_uong", "#112233", null);
        when(categoryRepository.existsByCode("an_uong")).thenReturn(true);

        assertThatThrownBy(() -> behavierService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_CODE_EXISTS.getCode());

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: icon không hợp lệ ném VALIDATION_ERROR")
    void create_invalidIcon_throwsException() {
        CategoryCreateReq req = new CategoryCreateReq("Ăn uống", "an_uong", "mô tả", "icon_sai", "#112233", null);
        when(categoryRepository.existsByCode("an_uong")).thenReturn(false);
        when(iconService.findByCode("icon_sai", false)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> behavierService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("create: tạo danh mục gốc thành công (level 0, path = code, isLeaf = true)")
    void create_rootCategory_success() {
        CategoryCreateReq req = new CategoryCreateReq("Ăn uống", "an_uong", "mô tả", "an_uong", "#112233", null);
        IconRes icon = new IconRes("an_uong", "Ăn uống", "M0 0");

        when(categoryRepository.existsByCode("an_uong")).thenReturn(false);
        when(iconService.findByCode("an_uong", false)).thenReturn(Optional.of(icon));
        when(categoryRepository.existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(eq("Ăn uống"), any())).thenReturn(false);

        CategoryRes expectedRes = new CategoryRes(
                UUID.randomUUID(), "Ăn uống", "an_uong", "mô tả", null, "M0 0", "#112233", 0, true, null, false);
        when(cateMapper.toResponse(any(Category.class), eq("M0 0"))).thenReturn(expectedRes);

        CategoryRes res = behavierService.create(req);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        Category saved = captor.getValue();

        assertThat(saved.getCode()).isEqualTo("an_uong");
        assertThat(saved.getLevel()).isEqualTo(0);
        assertThat(saved.getPath()).isEqualTo("an_uong");
        assertThat(saved.getParent()).isNull();
        assertThat(saved.getIsLeaf()).isTrue();
        assertThat(res.code()).isEqualTo("an_uong");
    }

    @Test
    @DisplayName("create: tạo danh mục con thành công, cha chuyển isLeaf thành false")
    void create_childCategory_success() {
        Category parent = Category.builder()
                .id(UUID.randomUUID())
                .code("an_uong")
                .name("Ăn uống")
                .level(0)
                .path("an_uong")
                .isLeaf(true)
                .build();
        parent.setIsDeleted(false);

        CategoryCreateReq req = new CategoryCreateReq("Cà phê", "ca_phe", "mô tả", "ca_phe", "#112233", "an_uong");
        IconRes icon = new IconRes("ca_phe", "Cà phê", "M1 1");

        when(categoryRepository.existsByCode("ca_phe")).thenReturn(false);
        when(iconService.findByCode("ca_phe", false)).thenReturn(Optional.of(icon));
        when(categoryRepository.findByCodeAndIsDeletedFalse("an_uong")).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(eq("Cà phê"), any())).thenReturn(false);

        CategoryRes expectedRes = new CategoryRes(
                UUID.randomUUID(), "Cà phê", "ca_phe", "mô tả", "an_uong", "M1 1", "#112233", 1, true, null, false);
        when(cateMapper.toResponse(any(Category.class), eq("M1 1"))).thenReturn(expectedRes);

        CategoryRes res = behavierService.create(req);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        Category saved = captor.getValue();

        assertThat(saved.getCode()).isEqualTo("ca_phe");
        assertThat(saved.getLevel()).isEqualTo(1);
        assertThat(saved.getPath()).isEqualTo("an_uong/ca_phe");
        assertThat(saved.getParent()).isEqualTo(parent);
        assertThat(parent.getIsLeaf()).isFalse();
        assertThat(res.parentCode()).isEqualTo("an_uong");
    }

    @Test
    @DisplayName("getMasters: trả danh sách danh mục gốc")
    void getMasters_returnsRootCategories() {
        Category c1 = Category.builder().id(UUID.randomUUID()).code("an_uong").iconCode("an_uong").build();
        when(categoryRepository.findByParentIsNullAndIsDeletedFalseOrderByNameAsc()).thenReturn(List.of(c1));
        when(iconService.findIconsByCodes(List.of("an_uong"))).thenReturn(Map.of("an_uong", "path1"));
        when(cateMapper.toResponse(c1, "path1")).thenReturn(new CategoryRes(c1.getId(), "Ăn uống", "an_uong", null, null, "path1", null, 0, true, null, false));

        List<CategoryRes> result = behavierService.getMasters();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("an_uong");
    }

    @Test
    @DisplayName("delete: xóa mềm danh mục và toàn bộ con cháu")
    void delete_softDeletesCategoryAndDescendants() {
        Category parent = Category.builder()
                .id(UUID.randomUUID())
                .code("goc")
                .isLeaf(false)
                .build();
        parent.setIsDeleted(false);

        Category target = Category.builder()
                .id(UUID.randomUUID())
                .code("ca_phe")
                .path("goc/ca_phe")
                .parent(parent)
                .build();
        target.setIsDeleted(false);

        Category child = Category.builder()
                .id(UUID.randomUUID())
                .code("espresso")
                .path("goc/ca_phe/espresso")
                .build();
        child.setIsDeleted(false);

        when(categoryRepository.findByCodeAndIsDeletedFalse("ca_phe")).thenReturn(Optional.of(target));
        when(categoryRepository.findDescendants("goc/ca_phe")).thenReturn(List.of(child));
        when(categoryRepository.existsByParent_IdAndIsDeletedFalse(parent.getId())).thenReturn(false);

        behavierService.delete("ca_phe");

        assertThat(target.getIsDeleted()).isTrue();
        assertThat(child.getIsDeleted()).isTrue();
        assertThat(parent.getIsLeaf()).isTrue();
    }
}
