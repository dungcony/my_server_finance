package com.datn.financeapp.debt.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body {@code GET /debts/summary} (api/08 mục 2).
 *
 * <p>{@code difference = receivable.total - payable.total}; ÂM nghĩa là đang nợ nhiều hơn được
 * nợ. Chỉ tính trên các khoản {@code outstanding} — khoản {@code settled}/{@code written_off}
 * không còn là tiền sẽ về hay sẽ đi.
 */
public record DebtSummaryResponse(Side receivable, Side payable, long difference, List<DueSoonItem> dueSoon) {

    public record Side(long total, int count, OverduePart isOverdue) {}

    public record OverduePart(long total, int count) {}

    public record DueSoonItem(
            UUID id,
            String counterpartyName,
            long remainingAmount,
            LocalDate dueDate,
            int daysRemaining,
            String type) {}
}
