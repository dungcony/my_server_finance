package com.datn.financeapp.transaction.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tham số lọc dùng chung cho GET /transactions và GET /transactions/by-date (api/04-GIAO-DICH.md
 * mục 1). {@code period} (week/month/quarter/year) ưu tiên hơn {@code fromDate}/{@code toDate}
 * nếu client gửi cả hai (api/00-QUY-UOC-CHUNG.md mục 7.2).
 */
public record TransactionFilterRequest(
        LocalDate fromDate,
        LocalDate toDate,
        String period,
        String type,
        UUID walletId,
        UUID categoryId,
        String source,
        Boolean countsInReport,
        String search,
        Long minAmount,
        Long maxAmount,
        Boolean includeTransfers) {

    public boolean includeTransfersOrDefault() {
        return includeTransfers == null || includeTransfers;
    }
}
