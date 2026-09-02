package com.datn.financeapp.transaction.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * GET /transactions/{id} (api/04-GIAO-DICH.md mục 3) — mọi field như {@link
 * TransactionListItemResponse} kèm thêm {@code relatedDebt}/{@code recurring} (Phase 4 — Phase 3
 * LUÔN trả {@code null}, không bịa dữ liệu) và {@code aiDrafts} (rút gọn từ bảng {@code
 * ai_drafts}, đã tồn tại từ V2, chỉ SELECT — không cần nghiệp vụ AI thật).
 */
public record TransactionDetailResponse(
        UUID id,
        String type,
        Long amount,
        LocalDate date,
        String displayName,
        String note,
        String source,
        /** Xem {@link TransactionListItemResponse#countsInReport()}. */
        Boolean countsInReport,
        TransactionListItemResponse.WalletRef wallet,
        TransactionListItemResponse.WalletRef destinationWallet,
        TransactionListItemResponse.CategoryRef category,
        String receiptUrl,
        UUID recurringId,
        UUID draftId,
        Instant createdAt,
        Instant updatedAt,
        Object relatedDebt,
        Object recurring,
        AiDraftRef aiDrafts) {

    public record AiDraftRef(UUID id, String method, String rawInput, Double confidence) {}
}
