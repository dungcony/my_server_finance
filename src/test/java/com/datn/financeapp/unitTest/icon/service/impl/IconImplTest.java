package com.datn.financeapp.unitTest.icon.service.impl;

import com.datn.financeapp.module.icon.service.impl.*;


import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.entity.Icon;
import com.datn.financeapp.module.icon.mapper.IconMapper;
import com.datn.financeapp.module.icon.repository.IconRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IconImplTest {

    @Mock
    private IconRepository iconRepository;

    @Mock
    private IconMapper iconMapper;

    private IconImpl iconImpl;

    @BeforeEach
    void setUp() {
        iconImpl = new IconImpl(iconRepository, iconMapper);
        lenient().when(iconMapper.toIconResponse(any(Icon.class))).thenAnswer(invocation -> {
            Icon icon = invocation.getArgument(0);
            return new IconRes(icon.getCode(), icon.getDisplayName(), icon.getPathData());
        });
    }

    @Test
    @DisplayName("findByCode: includeDeleted = false chỉ lấy icon chưa xoá")
    void findByCode_excludeDeleted_returnsActive() {
        Icon icon = icon("ca_phe", "Ly cà phê", "M1 1");
        when(iconRepository.findByCodeAndIsDeletedFalse("ca_phe")).thenReturn(Optional.of(icon));

        Optional<IconRes> result = iconImpl.findByCode("ca_phe", false);

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("ca_phe");
        assertThat(result.get().displayName()).isEqualTo("Ly cà phê");
    }

    @Test
    @DisplayName("findByCode: includeDeleted = true lấy cả icon đã xoá")
    void findByCode_includeDeleted_returnsAny() {
        Icon icon = icon("ca_phe", "Ly cà phê", "M1 1");
        icon.setIsDeleted(true);
        when(iconRepository.findByCode("ca_phe")).thenReturn(Optional.of(icon));

        Optional<IconRes> result = iconImpl.findByCode("ca_phe", true);

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("ca_phe");
    }

    @Test
    @DisplayName("findIconByCode: code null hoặc không tìm thấy trả về chuỗi rỗng")
    void findIconByCode_nullOrNotFound_returnsEmptyString() {
        assertThat(iconImpl.findIconByCode(null)).isEmpty();

        when(iconRepository.findByCodeAndIsDeletedFalse("unknown")).thenReturn(Optional.empty());
        assertThat(iconImpl.findIconByCode("unknown")).isEmpty();
    }

    @Test
    @DisplayName("findIconByCode: tìm thấy icon trả về pathData")
    void findIconByCode_found_returnsPathData() {
        Icon icon = icon("ca_phe", "Ly cà phê", "M1 1");
        when(iconRepository.findByCodeAndIsDeletedFalse("ca_phe")).thenReturn(Optional.of(icon));

        String path = iconImpl.findIconByCode("ca_phe");
        assertThat(path).isEqualTo("M1 1");
    }

    @Test
    @DisplayName("findIconsByCodes: tra danh sách icon theo mã")
    void findIconsByCodes_returnsMap() {
        Icon icon1 = icon("ca_phe", "Ly cà phê", "M1 1");
        when(iconRepository.findAllByCodeInAndIsDeleted(List.of("ca_phe", "xe_may"), false))
                .thenReturn(List.of(icon1));

        Map<String, String> map = iconImpl.findIconsByCodes(List.of("ca_phe", "xe_may"));

        assertThat(map).hasSize(2);
        assertThat(map.get("ca_phe")).isEqualTo("M1 1");
        assertThat(map.get("xe_may")).isEmpty();
    }

    private static Icon icon(String code, String displayName, String pathData) {
        Icon icon = Icon.builder()
                .id(UUID.randomUUID())
                .code(code)
                .displayName(displayName)
                .pathData(pathData)
                .build();
        icon.setIsDeleted(false);
        return icon;
    }
}
