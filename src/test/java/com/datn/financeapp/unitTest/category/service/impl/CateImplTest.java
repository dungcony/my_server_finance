package com.datn.financeapp.unitTest.category.service.impl;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.entity.Category;
import com.datn.financeapp.module.category.mapper.CateMapper;
import com.datn.financeapp.module.category.repository.CategoryRepository;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.category.service.impl.CateImpl;
import com.datn.financeapp.module.icon.service.IconService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CateImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private IconService iconService;

    @Mock
    private CateMapper cateMapper;

    private CategoryService cateService;

    @BeforeEach
    void setUp() {
        cateService = new CateImpl(categoryRepository, iconService, cateMapper);
    }

    @Test
    @DisplayName("findByCodeAndIsDeleted: code null hoặc rỗng trả về Optional.empty()")
    void findByCode_nullOrEmpty_returnsEmpty() {
        assertThat(cateService.findByCodeAndIsDeleted(null, false)).isEmpty();
        assertThat(cateService.findByCodeAndIsDeleted("", false)).isEmpty();
    }

    @Test
    @DisplayName("findByCodeAndIsDeleted: không tìm thấy category trong DB trả về Optional.empty()")
    void findByCode_notFound_returnsEmpty() {
        when(categoryRepository.findByCode("not_found")).thenReturn(Optional.empty());

        Optional<CateSumaryRes> result = cateService.findByCodeAndIsDeleted("not_found", false);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByCodeAndIsDeleted: includeDeleted = false nhưng category bị xoá trả về Optional.empty()")
    void findByCode_deletedAndIncludeDeletedFalse_returnsEmpty() {
        Category cate = Category.builder()
                .id(UUID.randomUUID())
                .code("an_uong")
                .isLeaf(true)
                .build();
        cate.setIsDeleted(true);

        when(categoryRepository.findByCode("an_uong")).thenReturn(Optional.of(cate));

        Optional<CateSumaryRes> result = cateService.findByCodeAndIsDeleted("an_uong", false);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByCodeAndIsDeleted: category không phải lá vẫn trả về CateSumaryRes thành công")
    void findByCode_notLeaf_returnsSummary() {
        Category cate = Category.builder()
                .id(UUID.randomUUID())
                .code("an_uong")
                .isLeaf(false)
                .build();
        cate.setIsDeleted(false);

        CateSumaryRes summary = new CateSumaryRes(UUID.randomUUID(), "Ăn uống", "an_uong", "M1 1", "#3d6b7d");

        when(categoryRepository.findByCode("an_uong")).thenReturn(Optional.of(cate));
        when(iconService.findIconByCode(any())).thenReturn("M1 1");
        when(cateMapper.toSumary(eq(cate), any())).thenReturn(summary);

        Optional<CateSumaryRes> result = cateService.findByCodeAndIsDeleted("an_uong", false);
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("findByCodeAndIsDeleted: category thoả mãn điều kiện trả về CateSumaryRes thành công")
    void findByCode_valid_returnsSummary() {
        Category cate = Category.builder()
                .id(UUID.randomUUID())
                .code("ca_phe")
                .name("Cà phê")
                .iconCode("ca_phe")
                .color("#3d6b7d")
                .isLeaf(true)
                .build();
        cate.setIsDeleted(false);

        CateSumaryRes summary = new CateSumaryRes(UUID.randomUUID(), "Cà phê", "ca_phe", "M1 1", "#3d6b7d");

        when(categoryRepository.findByCode("ca_phe")).thenReturn(Optional.of(cate));
        when(iconService.findIconByCode("ca_phe")).thenReturn("M1 1");
        when(cateMapper.toSumary(eq(cate), eq("M1 1"))).thenReturn(summary);

        Optional<CateSumaryRes> result = cateService.findByCodeAndIsDeleted("ca_phe", false);

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("ca_phe");
        assertThat(result.get().name()).isEqualTo("Cà phê");
        assertThat(result.get().icon()).isEqualTo("M1 1");
    }
}
