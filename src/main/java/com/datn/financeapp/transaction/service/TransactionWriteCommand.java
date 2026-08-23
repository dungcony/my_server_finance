package com.datn.financeapp.transaction.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tham số nguyên thuỷ cho {@link TransactionWriter#write}. KHÔNG phải DTO nghiệp vụ — D-31:
 * {@code TransactionWriter} không biết gì về nghiệp vụ gọi nó (transfer, adjustment, CRUD
 * người dùng, hay Phase 4 debt/goal/recurring).
 *
 * <p>Nếu {@code id == null}, {@link TransactionWriter#write} tự sinh {@code UUID.randomUUID()}.
 */
public record TransactionWriteCommand(
        UUID id,
        UUID userId,
        UUID walletId,
        UUID destinationWalletId,
        UUID categoryId,
        String type,
        long amount,
        LocalDate date,
        String note,
        String displayName,
        String source,
        boolean countsInReport,
        UUID recurringId,
        UUID draftId,
        String receiptUrl) {
}
