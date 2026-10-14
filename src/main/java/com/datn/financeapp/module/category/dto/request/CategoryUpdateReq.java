package com.datn.financeapp.module.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Yêu cầu cập nhật danh mục (chỉ admin). Trường {@code null} nghĩa là giữ nguyên giá trị cũ.
 *
 * @param name        Tên mới (tối đa 50 ký tự)
 * @param description Mô tả mới
 * @param iconId      Mã biểu tượng mới trong kho {@code icons}
 * @param color       Mã màu mới (định dạng Hex #RRGGBB)
 * @param parentCode  Mã danh mục cha mới; {@code null} là giữ nguyên cha (không hỗ trợ chuyển lên gốc)
 */
public record CategoryUpdateReq(
        @Size(max = 50, message = "Tên danh mục không được vượt quá 50 ký tự.") String name,
        String description,
        UUID iconId,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB.") String color,
        @NotBlank(message = "Mã danh mục cha không được để trống.") String parentCode
) {
}
