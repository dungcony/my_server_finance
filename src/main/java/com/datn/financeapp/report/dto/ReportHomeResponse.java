package com.datn.financeapp.report.dto;

import com.datn.financeapp.budget.dto.BudgetAlertResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /reports/home} (api/06-BAO-CAO.md mục 1) — gộp sẵn toàn bộ dữ liệu màn Tổng quan.
 *
 * <p>{@code aiRecommendation} của mẫu api/06 KHÔNG có trong response này — module AI (api/07)
 * chưa nằm trong phạm vi Phase 4, sẽ nối vào ở phase AI sau (xem SUMMARY plan 04-06, mục "Known
 * Stubs"). Client phải tự ẩn khối gợi ý AI khi trường này vắng mặt.
 */
public record ReportHomeResponse(
        Balance balance,
        List<WalletItem> topWallets,
        PeriodSummary periodSummary,
        DailyTrend dailyTrend,
        List<TopSpendingItem> topSpending,
        List<RecentTransactionItem> recentTransactions,
        List<BudgetAlertResponse> budgetsNeedingAttention) {

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
