package com.datn.financeapp.transaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body của PUT /transactions/{id} (api/04-GIAO-DICH.md mục 8) — "Nhận cùng bộ trường như tạo
 * mới". PUT là ghi đè toàn phần (không phải PATCH từng phần), nên giữ {@code type}/{@code amount}/
 * {@code walletId} bắt buộc giống {@link CreateTransactionRequest}.
 */
public record UpdateTransactionRequest(
        @NotNull @Pattern(regexp = "^(expense|income|transfer)$") String type,
        @NotNull Long amount,
        @NotNull UUID walletId,
        UUID destinationWalletId,
        UUID categoryId,
        LocalDate date,
        @Size(max = 150) String displayName,
        @Size(max = 500) String note,
        Boolean countsInReport,
        String receiptUrl) {
}
