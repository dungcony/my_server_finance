package com.datn.financeapp.unitTest.category.service.impl;

import com.datn.financeapp.module.category.service.impl.*;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.entity.Category;
import com.datn.financeapp.module.category.mapper.CateMapper;
import com.datn.financeapp.module.category.repository.CategoryRepository;
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

    private CateImpl cateService;

    @BeforeEach
    void setUp() {
        cateService = new CateImpl(categoryRepository, iconService, cateMapper);
    }

    @Test
    @DisplayName("findByCodeAndIsDeletedAndIsLeaf: code null hoặc rỗng trả về Optional.empty()")
    void findByCode_nullOrEmpty_returnsEmpty() {
        assertThat(cateService.findByCodeAndIsDeletedAndIsLeaf(null, false, false)).isEmpty();
        assertThat(cateService.findByCodeAndIsDeletedAndIsLeaf("", false, false)).isEmpty();
    }

    @Test
    @DisplayName("findByCodeAndIsDeletedAndIsLeaf: không tìm thấy category trong DB trả về Optional.empty()")
    void findByCode_notFound_returnsEmpty() {
        when(categoryRepository.findByCode("not_found")).thenReturn(Optional.empty());

        Optional<CateSumaryRes> result = cateService.findByCodeAndIsDeletedAndIsLeaf("not_found", false, false);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByCodeAndIsDeletedAndIsLeaf: includeDeleted = false nhưng category bị xoá trả về Optional.empty()")
    void findByCode_deletedAndIncludeDeletedFalse_returnsEmpty() {
        Category cate = Category.builder()
                .id(UUID.randomUUID())
                .code("an_uong")
                .isLeaf(true)
                .build();
        cate.setIsDeleted(true);

        when(categoryRepository.findByCode("an_uong")).thenReturn(Optional.of(cate));

        Optional<CateSumaryRes> result = cateService.findByCodeAndIsDeletedAndIsLeaf("an_uong", false, false);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByCodeAndIsDeletedAndIsLeaf: isLeaf = true nhưng category không phải lá trả về Optional.empty()")
    void findByCode_notLeafAndIsLeafTrue_returnsEmpty() {
        Category cate = Category.builder()
                .id(UUID.randomUUID())
                .code("an_uong")
                .isLeaf(false)
                .build();
        cate.setIsDeleted(false);

        when(categoryRepository.findByCode("an_uong")).thenReturn(Optional.of(cate));

        Optional<CateSumaryRes> result = cateService.findByCodeAndIsDeletedAndIsLeaf("an_uong", false, true);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByCodeAndIsDeletedAndIsLeaf: category thoả mãn điều kiện trả về CateSumaryRes thành công")
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

        Optional<CateSumaryRes> result = cateService.findByCodeAndIsDeletedAndIsLeaf("ca_phe", false, true);

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("ca_phe");
        assertThat(result.get().name()).isEqualTo("Cà phê");
        assertThat(result.get().icon()).isEqualTo("M1 1");
    }
}
