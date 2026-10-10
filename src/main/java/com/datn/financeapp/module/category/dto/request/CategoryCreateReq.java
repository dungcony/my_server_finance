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
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[a-z0-9_]+$", message = "Mã chỉ gồm chữ thường, số và dấu gạch dưới.") String code,
        @NotBlank String description,
        @NotBlank String iconCode,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB.") String color,
        @NotBlank String parentCode
) {

}
