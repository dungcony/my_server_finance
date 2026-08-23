package com.datn.financeapp.transaction.dto;

import java.util.List;
import java.util.UUID;

/**
 * Phản hồi POST /transactions/bulk (TXN-04, api/04-GIAO-DICH.md mục 5). {@code rowErrors} gom
 * lỗi từng dòng (D-34 — dòng lỗi không chặn dòng khác), {@code newBalance} gom số dư MỚI NHẤT
 * theo từng ví chạm tới trong cả lô (không lặp cùng ví nhiều lần).
 */
public record BulkCreateTransactionResponse(
        int successCount,
        int failureCount,
        List<TransactionListItemResponse> transaction,
        List<RowError> rowErrors,
        List<NewBalanceItem> newBalance) {

    public record RowError(int rowIndex, String code, String message) {}

    public record NewBalanceItem(UUID walletId, Long balance) {}
}
