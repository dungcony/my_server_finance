package com.datn.financeapp.module.budget.helper;

import com.datn.financeapp.module.budget.enums.BudgetStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Tiện ích tính toán tiến độ chi tiêu, tỉ lệ phần trăm và trạng thái cảnh báo của ngân sách.
 */
public record BudgetProgress(
        long limit,
        long spent,
        long remaining,
        BigDecimal ratio,
        BudgetStatus status,
        int daysRemaining
) {

    private static final BigDecimal NEAR_LIMIT_THRESHOLD = new BigDecimal("0.8");
    private static final BigDecimal OVER_LIMIT_THRESHOLD = BigDecimal.ONE;

    public static BudgetProgress of(long limit, long spent, LocalDate endDate, LocalDate today) {
        long remaining = limit - spent;
        BigDecimal ratio = limit <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(spent)
                .divide(BigDecimal.valueOf(limit), 4, RoundingMode.HALF_UP);

        BudgetStatus status;
        if (ratio.compareTo(OVER_LIMIT_THRESHOLD) >= 0) {
            status = BudgetStatus.OVER_LIMIT;
        } else if (ratio.compareTo(NEAR_LIMIT_THRESHOLD) >= 0) {
            status = BudgetStatus.NEAR_LIMIT;
        } else {
            status = BudgetStatus.NORMAL;
        }

        int daysRemaining = 0;
        if (endDate != null && today != null && !today.isAfter(endDate)) {
            daysRemaining = (int) ChronoUnit.DAYS.between(today, endDate);
        }

        return new BudgetProgress(limit, spent, remaining, ratio, status, daysRemaining);
    }
}
