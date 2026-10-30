package com.datn.financeapp.module.report.dto.response;

import java.time.LocalDate;

/**
 * Response DTO cho báo cáo tóm tắt tình hình thu chi và so sánh với kỳ trước.
 *
 * @param period           thông tin chu kỳ báo cáo
 * @param openingBalance   số dư đầu kỳ
 * @param closingBalance   số dư cuối kỳ
 * @param totalIncome      tổng thu nhập trong kỳ
 * @param totalExpense     tổng chi tiêu trong kỳ
 * @param netIncome        thu nhập ròng (tổng thu - tổng chi)
 * @param transactionCount số lượng giao dịch trong kỳ
 * @param avgDailyExpense  chi tiêu trung bình mỗi ngày
 * @param vsPreviousPeriod thông tin so sánh chi tiêu với kỳ trước
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

    /**
     * Thông tin chi tiết về chu kỳ thời gian báo cáo.
     *
     * @param label    nhãn chu kỳ
     * @param fromDate ngày bắt đầu
     * @param toDate   ngày kết thúc
     */
    public record PeriodInfo(String label, LocalDate fromDate, LocalDate toDate) {
    }

    /**
     * Thông tin so sánh chi tiêu với kỳ trước đó.
     *
     * @param previousPeriodExpense tổng chi tiêu kỳ trước
     * @param difference            mức chênh lệch số tiền
     * @param changeRatio           tỷ lệ thay đổi (tăng/giảm)
     * @param label                 nhãn mô tả so sánh
     */
    public record VsPreviousPeriod(
            Long previousPeriodExpense, Long difference, Double changeRatio, String label) {
    }
}
