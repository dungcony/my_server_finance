package com.datn.financeapp.module.transaction.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một phần tử giao dịch, dùng cho GET /transactions (list/detail — plan 03-03) và phản hồi
 * create/update/duplicate ở plan này (api/04-GIAO-DICH.md mục 1 và mục 4).
 */
public record TransactionDetailResponse(
        UUID id,
        String type,
        Long amount,
        LocalDate date,
        String displayName,
        String note,
        String source,
        String receiptUrl,
        UUID recurringId,
        Instant createdAt,
        Instant updatedAt) {

}
