package com.datn.financeapp.report.dto.response;

import java.math.BigDecimal;
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
        List<BudgetAttentionItem> budgetsNeedingAttention) {

    /**
     * Một dòng của khối "ngân sách cần chú ý" trên màn Tổng quan.
     *
     * <p>Hình dạng riêng, KHÔNG dùng lại {@code BudgetAlertResponse} của {@code GET
     * /budgets/alerts}: api/06 mục 1 quy định khối này chỉ có {@code id}/{@code category}/{@code
     * ratio}/{@code status}, còn api/05 mục 7 quy định khối kia có thêm {@code percent_label},
     * {@code title}, {@code content}, {@code suggestion} và đặt tên khoá khác ({@code budget_id},
     * {@code severity}). Dùng chung một record khiến response lệch đặc tả mà không bên nào báo
     * lỗi — client đọc {@code id} nhận về null và sập màn Tổng quan (FIX-06, đợt test 02/09/2026).
     *
     * <p>{@code status} giữ nguyên giá trị gốc của {@code v_budget_progress}
     * ({@code near_limit}/{@code over_limit}), không quy đổi sang {@code alert}/{@code critical}
     * như api/05 — hai đặc tả cố ý khác nhau ở điểm này.
     */
    public record BudgetAttentionItem(UUID id, String category, BigDecimal ratio, String status) {}

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
