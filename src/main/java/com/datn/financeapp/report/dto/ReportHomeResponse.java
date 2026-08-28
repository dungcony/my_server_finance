package com.datn.financeapp.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code GET /reports/home} (api/06-BAO-CAO.md mục 1) — gộp sẵn toàn bộ dữ liệu màn Tổng quan. */
public record ReportHomeResponse(
        Balance balance,
        List<WalletItem> topWallets,
        PeriodSummary periodSummary,
        DailyTrend dailyTrend,
        List<TopSpendingItem> topSpending,
        List<RecentTransactionItem> recentTransactions) {

    public record Balance(Long personalTotal, Long sharedTotal) {}

    public record WalletItem(UUID id, String name, Long currentBalance, String type) {}

    public record PeriodSummary(String periodLabel, Long totalIncome, Long totalExpense, Long difference) {}

    public record DailyTrend(
            List<DailyTrendResponse.CurrentPoint> currentPoints,
            List<DailyTrendResponse.AveragePoint> averageLine,
            Long maxValue,
            Note note) {

        public record Note(String highestSpendDate, Long amountThatDay, Long avgPrevious3Months) {}
    }

    public record TopSpendingItem(
            UUID categoryId, String name, Long amount, Double ratio, IconRef icon, String color) {}

    public record IconRef(String code, String pathData) {}

    public record RecentTransactionItem(
            UUID id,
            String type,
            Long amount,
            LocalDate date,
            String displayName,
            CategoryRef category,
            WalletRef wallet) {}

    public record CategoryRef(String name, IconRef icon, String color) {}

    public record WalletRef(String name) {}
}
