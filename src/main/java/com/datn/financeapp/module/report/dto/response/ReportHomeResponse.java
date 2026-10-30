package com.datn.financeapp.module.report.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO tổng hợp cho màn hình trang chủ báo cáo.
 *
 * @param balance                 thông tin số dư tài chính
 * @param topWallets              danh sách các ví hàng đầu
 * @param periodSummary           tóm tắt thu chi trong chu kỳ
 * @param dailyTrend              xu hướng chi tiêu theo từng ngày
 * @param topSpending             danh sách các danh mục chi tiêu nhiều nhất
 * @param recentTransactions      danh sách các giao dịch gần đây
 * @param budgetsNeedingAttention danh sách ngân sách cần chú ý
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
     * Thông tin item ngân sách cần chú ý.
     *
     * @param id       ID ngân sách
     * @param category tên danh mục
     * @param ratio    tỷ lệ tiêu dùng ngân sách
     * @param status   trạng thái cảnh báo (near_limit, over_limit)
     */
    public record BudgetAttentionItem(UUID id, String category, BigDecimal ratio, String status) {
    }

    /**
     * Thông tin tổng số dư tài chính.
     *
     * @param personalTotal tổng số dư cá nhân
     * @param sharedTotal   tổng số dư dùng chung
     */
    public record Balance(Long personalTotal, Long sharedTotal) {
    }

    /**
     * Thông tin tóm tắt một ví.
     *
     * @param id             ID ví
     * @param name           tên ví
     * @param currentBalance số dư hiện tại
     * @param type           loại ví
     */
    public record WalletItem(UUID id, String name, Long currentBalance, String type) {
    }

    /**
     * Tóm tắt chu kỳ báo cáo.
     *
     * @param periodLabel  nhãn chu kỳ
     * @param totalIncome  tổng thu nhập
     * @param totalExpense tổng chi tiêu
     * @param difference   chênh lệch thu chi
     */
    public record PeriodSummary(String periodLabel, Long totalIncome, Long totalExpense, Long difference) {
    }

    /**
     * Xu hướng chi tiêu theo ngày.
     *
     * @param currentPoints các điểm chi tiêu thực tế
     * @param averageLine   đường chi tiêu trung bình
     * @param maxValue      giá trị lớn nhất
     * @param note          ghi chú chi tiết
     */
    public record DailyTrend(
            List<DailyTrendResponse.CurrentPoint> currentPoints,
            List<DailyTrendResponse.AveragePoint> averageLine,
            Long maxValue,
            Note note) {

        /**
         * Ghi chú cho điểm chi tiêu cao nhất.
         *
         * @param highestSpendDate   ngày chi tiêu cao nhất
         * @param amountThatDay      số tiền chi trong ngày đó
         * @param avgPrevious3Months trung bình 3 tháng trước
         */
        public record Note(String highestSpendDate, Long amountThatDay, Long avgPrevious3Months) {
        }
    }

    /**
     * Thông tin danh mục chi tiêu hàng đầu.
     *
     * @param categoryId ID danh mục
     * @param name       tên danh mục
     * @param amount     số tiền chi tiêu
     * @param ratio      tỷ lệ trên tổng chi
     * @param icon       biểu tượng
     * @param color      màu sắc
     */
    public record TopSpendingItem(
            UUID categoryId, String name, Long amount, Double ratio, IconRef icon, String color) {
    }

    /**
     * Biểu tượng danh mục.
     *
     * @param code     mã biểu tượng
     * @param pathData dữ liệu SVG path
     */
    public record IconRef(String code, String pathData) {
    }

    /**
     * Giao dịch gần đây.
     *
     * @param id          ID giao dịch
     * @param type        loại giao dịch
     * @param amount      số tiền
     * @param date        ngày thực hiện
     * @param displayName tên hiển thị
     * @param category    thông tin danh mục
     * @param wallet      thông tin ví
     */
    public record RecentTransactionItem(
            UUID id,
            String type,
            Long amount,
            LocalDate date,
            String displayName,
            CategoryRef category,
            WalletRef wallet) {
    }

    /**
     * Thông tin tham chiếu danh mục.
     *
     * @param name  tên danh mục
     * @param icon  biểu tượng
     * @param color màu sắc
     */
    public record CategoryRef(String name, IconRef icon, String color) {
    }

    /**
     * Thông tin tham chiếu ví.
     *
     * @param name tên ví
     */
    public record WalletRef(String name) {
    }
}
