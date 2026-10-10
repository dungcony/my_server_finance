package com.datn.financeapp.module.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu tạo mới danh mục thu/chi (chỉ admin).
 *
 * @param name        Tên danh mục (tối đa 50 ký tự)
 * @param code        Mã danh mục, duy nhất toàn hệ thống, chỉ gồm chữ thường, số và dấu gạch dưới
 * @param description Mô tả danh mục
 * @param iconCode    Mã biểu tượng trong kho {@code icons}
 * @param color       Mã màu hiển thị (định dạng Hex #RRGGBB)
 * @param parentCode  Mã danh mục cha ({@code null} tức là danh mục gốc)
 */
public record CategoryCreateReq(
        @NotBlank(message = "Tên danh mục không được để trống.") @Size(max = 50, message = "Tên danh mục không được vượt quá 50 ký tự.") String name,
        @NotBlank(message = "Mã danh mục không được để trống.") @Size(max = 50, message = "Mã danh mục không được vượt quá 50 ký tự.") @Pattern(regexp = "^[a-z0-9_]+$", message = "Mã chỉ gồm chữ thường, số và dấu gạch dưới.") String code,
        @NotBlank(message = "Mô tả không được để trống.") String description,
        @NotBlank(message = "Biểu tượng không được để trống.") String iconCode,
        @NotBlank(message = "Màu sắc không được để trống.") @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB.") String color,
        @NotBlank(message = "Mã danh mục cha không được để trống.") String parentCode
) {

}
