package com.datn.financeapp.report.dto;

import java.time.LocalDate;

/**
 * {@code GET /reports/summary} (api/06-BAO-CAO.md mục 2). {@code openingBalance} suy ngược từ
 * {@code closingBalance} theo công thức {@code closing - income + expense} — nhanh hơn cộng toàn
 * bộ lịch sử và cho cùng kết quả (api/06 mục 2 "Cách tính số dư đầu kỳ").
 */
public record ReportSummaryResponse(
        PeriodInfo period,
        Long openingBalance,
        Long closingBalance,
        Long totalIncome,
        Long totalExpense,
        Long netIncome,
        Long transactionCount,
        Long avgDailyExpense,
        VsPreviousPeriod vsPreviousPeriod) {

    public record PeriodInfo(String label, LocalDate fromDate, LocalDate toDate) {}

    public record VsPreviousPeriod(
            Long previousPeriodExpense, Long difference, Double changeRatio, String label) {}
}
