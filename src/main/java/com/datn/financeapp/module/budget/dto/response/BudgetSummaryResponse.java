package com.datn.financeapp.module.budget.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dữ liệu tổng hợp tổng thể về các ngân sách chi tiêu đang hiệu lực.
 *
 * @param period         Thông tin kỳ ngân sách đại diện
 * @param totalLimit     Tổng hạn mức chi tiêu của tất cả ngân sách (Đơn vị: VND)
 * @param totalSpent     Tổng số tiền đã chi tiêu (Đơn vị: VND)
 * @param totalRemaining Tổng số tiền còn lại có thể chi (Đơn vị: VND)
 * @param ratio          Tỷ lệ tổng số tiền chi trên tổng hạn mức (VD: 0.65 = 65%)
 * @param status         Trạng thái cảnh báo tổng thể ({@code normal}, {@code near_limit}, {@code over_limit})
 * @param budgetCount    Tổng số lượng ngân sách đang hiệu lực
 * @param overLimitCount Số lượng ngân sách đã vượt hạn mức
 * @param nearLimitCount Số lượng ngân sách sắp chạm hạn mức
 */
public record BudgetSummaryResponse(
        Period period,
        Long totalLimit,
        Long totalSpent,
        Long totalRemaining,
        BigDecimal ratio,
        String status,
        int budgetCount,
        int overLimitCount,
        int nearLimitCount) {

    /**
     * Thông tin kỳ ngân sách đại diện.
     *
     * @param periodType    Loại kỳ ngân sách (VD: {@code month})
     * @param label         Nhãn hiển thị kỳ
     * @param startDate     Ngày bắt đầu kỳ
     * @param endDate       Ngày kết thúc kỳ
     * @param daysRemaining Số ngày còn lại trong kỳ
     */
    public record Period(
            String periodType, String label, LocalDate startDate, LocalDate endDate, Integer daysRemaining) {
    }
}
