package com.datn.financeapp.transaction.dto;

import java.util.List;
import java.util.UUID;

/**
 * Phản hồi 201 của POST /transactions (và POST /transactions/{id}/duplicate — api/04-GIAO-DICH.md
 * mục 4 và mục 10). Trường {@code affectedBudgets} thuộc Phase 4 (module ngân sách chưa tồn tại) —
 * Phase 3 luôn trả rỗng, KHÔNG bịa dữ liệu.
 */
public record CreateTransactionResponse(TransactionResponse transaction, NewBalance newBalance, List<Object> affectedBudgets) {

    public CreateTransactionResponse(TransactionResponse transaction, NewBalance newBalance) {
        this(transaction, newBalance, List.of());
    }

    public record NewBalance(UUID walletId, Long balance) {
    }
}
