package com.datn.financeapp.module.budget.helper;

import com.datn.financeapp.module.budget.enums.BudgetStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Tiến độ chi tiêu của một ngân sách, tính từ hạn mức và số đã chi. Chỗ DUY NHẤT định nghĩa ngưỡng
 * cảnh báo: từ 80% là {@link BudgetStatus#NEAR_LIMIT}, từ 100% là {@link BudgetStatus#OVER_LIMIT}.
 *
 * @param remaining     còn lại (hạn mức − đã chi), âm khi vượt
 * @param ratio         tỉ lệ đã chi trên hạn mức, 4 chữ số thập phân
 * @param status        trạng thái theo tỉ lệ
 * @param daysRemaining số ngày còn lại tới hết kỳ, không âm
 */
public record BudgetProgress(long remaining, BigDecimal ratio, BudgetStatus status, int daysRemaining) {

    private static final BigDecimal NEAR_LIMIT_RATIO = new BigDecimal("0.8");
    private static final int RATIO_SCALE = 4;

    /**
     * Tính tiến độ. Hạn mức 0 thì tỉ lệ là 0 để khỏi chia cho 0.
     *
     * @param limit   hạn mức
     * @param spent   số đã chi
     * @param endDate ngày cuối kỳ
     * @param today   hôm nay
     */
    public static BudgetProgress of(long limit, long spent, LocalDate endDate, LocalDate today) {
        BigDecimal ratio = limit == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(spent).divide(BigDecimal.valueOf(limit), RATIO_SCALE, RoundingMode.HALF_UP);
        int daysRemaining = (int) Math.max(ChronoUnit.DAYS.between(today, endDate), 0);
        return new BudgetProgress(limit - spent, ratio, statusOf(ratio), daysRemaining);
    }

    private static BudgetStatus statusOf(BigDecimal ratio) {
        if (ratio.compareTo(BigDecimal.ONE) >= 0) {
            return BudgetStatus.OVER_LIMIT;
        }
        if (ratio.compareTo(NEAR_LIMIT_RATIO) >= 0) {
            return BudgetStatus.NEAR_LIMIT;
        }
        return BudgetStatus.NORMAL;
    }
}
