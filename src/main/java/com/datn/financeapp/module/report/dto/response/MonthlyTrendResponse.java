package com.datn.financeapp.module.report.dto.response;

import java.util.List;

/**
 * Response DTO cho báo cáo xu hướng thu chi theo các tháng.
 *
 * @param months         danh sách thông tin thu chi từng tháng
 * @param averageExpense chi tiêu trung bình mỗi tháng
 * @param highestMonth   tháng có chi tiêu cao nhất
 * @param lowestMonth    tháng có chi tiêu thấp nhất
 */
public record MonthlyTrendResponse(
        List<MonthItem> months, Long averageExpense, MonthAmount highestMonth, MonthAmount lowestMonth) {

    /**
     * Thông tin chi tiết thu chi trong một tháng.
     *
     * @param month        tháng thống kê (dạng YYYY-MM)
     * @param label        nhãn hiển thị (ví dụ: T1, T2)
     * @param totalIncome  tổng thu nhập
     * @param totalExpense tổng chi tiêu
     * @param difference   chênh lệch thu chi (thu - chi)
     */
    public record MonthItem(String month, String label, Long totalIncome, Long totalExpense, Long difference) {
    }

    /**
     * Tóm tắt tháng và số tiền chi tiêu.
     *
     * @param month  tháng thống kê (dạng YYYY-MM)
     * @param amount số tiền chi tiêu
     */
    public record MonthAmount(String month, Long amount) {
    }
}
