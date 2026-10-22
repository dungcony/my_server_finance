package com.datn.financeapp.unitTest.icon.service.impl;

import com.datn.financeapp.module.icon.service.impl.*;


import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.icon.dto.request.IconCreateReq;
import com.datn.financeapp.module.icon.dto.request.IconUpdateReq;
import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.entity.Icon;
import com.datn.financeapp.module.icon.mapper.IconMapper;
import com.datn.financeapp.module.icon.repository.IconRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IconBehavierImplTest {

    @Mock
    private IconRepository iconRepository;

    @Mock
    private IconMapper iconMapper;

    private IconBehavierImpl iconBehavier;

    @BeforeEach
    void setUp() {
        iconBehavier = new IconBehavierImpl(iconRepository, iconMapper);
        lenient().when(iconMapper.toIconResponse(any(Icon.class))).thenAnswer(invocation -> {
            Icon icon = invocation.getArgument(0);
            return new IconRes(icon.getCode(), icon.getDisplayName(), icon.getPathData());
        });
    }

    @Test
    @DisplayName("Tạo icon thành công khi mã chưa tồn tại")
    void create_valid_success() {
        IconCreateReq req = new IconCreateReq(
                "xe_dap", "Xe đạp", "M0 0h10", "di_lai", "phương tiện,xe", 2);
        when(iconRepository.existsByCode("xe_dap")).thenReturn(false);

        IconRes res = iconBehavier.create(req);

        ArgumentCaptor<Icon> captor = ArgumentCaptor.forClass(Icon.class);
        verify(iconRepository).save(captor.capture());
        Icon saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).isEqualTo("xe_dap");
        assertThat(saved.getDisplayName()).isEqualTo("Xe đạp");
        assertThat(saved.getPathData()).isEqualTo("M0 0h10");
        assertThat(res.code()).isEqualTo("xe_dap");
    }

    @Test
    @DisplayName("Tạo icon trùng code ném ra lỗi ICON_CODE_EXISTS")
    void create_duplicateCode_throwsException() {
        IconCreateReq req = new IconCreateReq(
                "an_uong", "Ăn uống", "M0 0", "an_uong", null, 0);
        when(iconRepository.existsByCode("an_uong")).thenReturn(true);

        assertThatThrownBy(() -> iconBehavier.create(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ICON_CODE_EXISTS.getCode());

        verify(iconRepository, never()).save(any(Icon.class));
    }

    @Test
    @DisplayName("Lấy danh sách icon phân trang dạng Slice")
    void getAll_returnsSliceOfIcons() {
        Pageable pageable = PageRequest.of(0, 10);
        Icon icon1 = icon("an_uong", "Ăn uống");
        Icon icon2 = icon("di_lai", "Đi lại");
        Slice<Icon> iconSlice = new SliceImpl<>(List.of(icon1, icon2), pageable, false);

        when(iconRepository.findByIsDeletedFalse(pageable)).thenReturn(iconSlice);

        Slice<IconRes> result = iconBehavier.getAll(pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(IconRes::code).containsExactly("an_uong", "di_lai");
    }

    @Test
    @DisplayName("Cập nhật icon thành công các trường được cung cấp")
    void update_valid_updatesFields() {
        Icon existing = icon("an_uong", "Ăn uống");

        when(iconRepository.findByCodeAndIsDeletedFalse("an_uong")).thenReturn(Optional.of(existing));

        IconUpdateReq req = new IconUpdateReq("Ăn uống & Cà phê", null, "M1 1");

        IconRes res = iconBehavier.update("an_uong", req);

        assertThat(existing.getDisplayName()).isEqualTo("Ăn uống & Cà phê");
        assertThat(existing.getPathData()).isEqualTo("M1 1");
        assertThat(res.displayName()).isEqualTo("Ăn uống & Cà phê");
    }

    @Test
    @DisplayName("Cập nhật icon không tồn tại ném ra lỗi ICON_NOT_FOUND")
    void update_notFound_throwsException() {
        when(iconRepository.findByCodeAndIsDeletedFalse("unknown")).thenReturn(Optional.empty());

        IconUpdateReq req = new IconUpdateReq("Mới", null, null);

        assertThatThrownBy(() -> iconBehavier.update("unknown", req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ICON_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("Xóa mềm icon đặt isDeleted = true")
    void delete_valid_setsDeleted() {
        Icon existing = icon("ca_phe", "Cà phê");
        existing.setIsDeleted(false);

        when(iconRepository.findByCodeAndIsDeletedFalse("ca_phe")).thenReturn(Optional.of(existing));

        IconRes res = iconBehavier.delete("ca_phe");

        assertThat(existing.getIsDeleted()).isTrue();
        assertThat(res.code()).isEqualTo("ca_phe");
    }

    @Test
    @DisplayName("Xóa mềm icon không tồn tại ném ra lỗi ICON_NOT_FOUND")
    void delete_notFound_throwsException() {
        when(iconRepository.findByCodeAndIsDeletedFalse("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> iconBehavier.delete("unknown"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ICON_NOT_FOUND.getCode());
    }

    private static Icon icon(String code, String displayName) {
        Icon icon = Icon.builder()
                .id(UUID.randomUUID())
                .code(code)
                .displayName(displayName)
                .pathData("M0 0")
                .build();
        icon.setIsDeleted(false);
        return icon;
    }
}
