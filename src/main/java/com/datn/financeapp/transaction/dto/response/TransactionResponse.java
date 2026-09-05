package com.datn.financeapp.transaction.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một phần tử giao dịch, dùng cho GET /transactions (list/detail — plan 03-03) và phản hồi
 * create/update/duplicate ở plan này (api/04-GIAO-DICH.md mục 1 và mục 4).
 */
public record TransactionResponse(
        UUID id,
        String type,
        Long amount,
        LocalDate date,
        String displayName,
        String note,
        String source,
        /** Xem {@link TransactionListItemResponse#countsInReport()}. */
        Boolean countsInReport,
        WalletRef wallet,
        WalletRef destinationWallet,
        CategoryRef category,
        String receiptUrl,
        UUID recurringId,
        UUID draftId,
        Instant createdAt,
        Instant updatedAt) {

    public record WalletRef(UUID id, String name, String type) {
    }

    public record CategoryRef(UUID id, String name, String type, UUID parentId) {
    }
}
