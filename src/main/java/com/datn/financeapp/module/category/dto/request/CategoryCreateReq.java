package com.datn.financeapp.module.category.dto.request;

import com.datn.financeapp.module.category.enums.CateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Yêu cầu tạo mới danh mục thu/chi tùy chỉnh.
 *
 * @param name       Tên danh mục (tối đa 50 ký tự)
 * @param type       Loại danh mục ({@code expense} hoặc {@code income})
 * @param iconId     ID biểu tượng của danh mục
 * @param color      Mã màu hiển thị (định dạng Hex #RRGGBB)
 * @param parentCode Mã cate cha(null tức mà master)
 */
public record CategoryCreateReq(
        @NotBlank String name,
        @NotNull @NotBlank String code,
        @NotNull CateType type,
        @NotBlank String description,
        @NotNull UUID iconId,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB.") String color,
        String parentCode
) {
}
