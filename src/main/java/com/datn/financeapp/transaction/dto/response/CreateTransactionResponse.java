package com.datn.financeapp.transaction.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Phản hồi 201 của POST /transactions (và POST /transactions/{id}/duplicate — api/04-GIAO-DICH.md
 * mục 4 và mục 10). {@code affectedBudgets} PHẢI được tính thật (Task 3, plan 06-01) — không có
 * constructor rút gọn để tránh lặp lại lỗi "quên truyền nên luôn rỗng".
 */
public record CreateTransactionResponse(
        TransactionResponse transaction, NewBalance newBalance, List<AffectedBudgetResponse> affectedBudgets) {

    public record NewBalance(UUID walletId, Long balance) {
    }
}
