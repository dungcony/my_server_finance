package com.datn.financeapp.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body của POST /categories (api/03-DANH-MUC.md mục 3). */
public record CreateCategoryRequest(
        @NotBlank @Size(min = 1, max = 50) String name,
        @NotBlank @Pattern(regexp = "^(expense|income)$") String type,
        @NotNull UUID iconId,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB.") String color,
        UUID parentCategoryId,
        UUID categoryGroupId) {}
