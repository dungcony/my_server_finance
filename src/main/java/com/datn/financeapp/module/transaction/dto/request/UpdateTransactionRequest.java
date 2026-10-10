package com.datn.financeapp.module.transaction.dto.request;

import com.datn.financeapp.module.transaction.enums.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body của PUT /transactions/{id} (api/04-GIAO-DICH.md mục 8) — "Nhận cùng bộ trường như tạo
 * mới". PUT là ghi đè toàn phần (không phải PATCH từng phần), nên giữ {@code type}/{@code amount}/
 * {@code walletId} bắt buộc giống {@link CreateTransactionRequest}.
 */
public record UpdateTransactionRequest(
        @NotNull TransactionType type,
        @NotNull Long amount,
        @NotNull UUID walletId,
        UUID categoryId,
        LocalDate date,
        @Size(max = 150) String displayName,
        @Size(max = 500) String note,
        String receiptUrl) {
}
