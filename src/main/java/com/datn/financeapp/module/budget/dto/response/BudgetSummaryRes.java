package com.datn.financeapp.module.budget.dto.response;

import com.datn.financeapp.module.budget.enums.BudgetStatus;

import java.math.BigDecimal;

/**
 * Tổng quan các ngân sách đang hiệu lực trong kỳ hiện tại.
 *
 * @param totalLimit     tổng hạn mức
 * @param totalSpent     tổng đã chi
 * @param totalRemaining tổng còn lại, âm khi vượt
 * @param ratio          tỉ lệ tổng đã chi trên tổng hạn mức
 * @param status         trạng thái tính trên tổng
 * @param budgetCount    số ngân sách
 * @param overLimitCount số ngân sách đã vượt hạn mức
 * @param nearLimitCount số ngân sách sắp chạm hạn mức
 */
public record BudgetSummaryRes(
        long totalLimit,
        long totalSpent,
        long totalRemaining,
        BigDecimal ratio,
        BudgetStatus status,
        int budgetCount,
        int overLimitCount,
        int nearLimitCount) {
}
