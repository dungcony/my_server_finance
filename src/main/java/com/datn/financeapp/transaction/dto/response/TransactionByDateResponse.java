package com.datn.financeapp.transaction.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /transactions/by-date (api/04-GIAO-DICH.md mục 2) — gom giao dịch theo ngày, phục vụ màn
 * Sổ giao dịch. Không phân trang theo đặc tả.
 */
public record TransactionByDateResponse(PeriodSummaryDto periodSummary, List<DayGroupDto> days) {

    public record PeriodSummaryDto(String periodLabel, Long totalIncome, Long totalExpense, Long difference) {}

    public record DayGroupDto(
            LocalDate date,
            String dayNumber,
            String dayLabel,
            String monthYear,
            Long dayTotal,
            List<TransactionListItemResponse> transaction) {}
}
