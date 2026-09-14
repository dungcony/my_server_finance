package com.datn.financeapp.category.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Body của {@code PATCH /wallets/{walletId}/categories/{categoryId}} (api/03-DANH-MUC.md mục 9).
 *
 * <p>Kiểu {@code Boolean} bọc chứ không phải {@code boolean} nguyên thuỷ: thiếu trường thì
 * {@code @NotNull} bắt được, còn nguyên thuỷ sẽ âm thầm nhận {@code false} và tắt mất danh mục
 * người dùng không định tắt.
 */
public record ToggleWalletCategoryRequest(@NotNull Boolean isEnabled) {}
