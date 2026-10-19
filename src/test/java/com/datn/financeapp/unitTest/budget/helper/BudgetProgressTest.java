package com.datn.financeapp.unitTest.budget.helper;

import com.datn.financeapp.module.budget.helper.*;


import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.module.budget.enums.BudgetStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetProgressTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("Dưới 80% hạn mức là bình thường")
    void below80Percent_isNormal() {
        BudgetProgress progress = BudgetProgress.of(3_000_000, 2_399_000, END, TODAY);

        assertThat(progress.status()).isEqualTo(BudgetStatus.NORMAL);
        assertThat(progress.remaining()).isEqualTo(601_000L);
    }

    @Test
    @DisplayName("Đúng 80% hạn mức là sắp chạm hạn mức")
    void exactly80Percent_isNearLimit() {
        BudgetProgress progress = BudgetProgress.of(3_000_000, 2_400_000, END, TODAY);

        assertThat(progress.status()).isEqualTo(BudgetStatus.NEAR_LIMIT);
        assertThat(progress.ratio()).isEqualByComparingTo("0.8");
    }

    @Test
    @DisplayName("Đúng 100% hạn mức là đã vượt; còn lại âm khi chi quá")
    void atOrAbove100Percent_isOverLimit() {
        assertThat(BudgetProgress.of(3_000_000, 3_000_000, END, TODAY).status()).isEqualTo(BudgetStatus.OVER_LIMIT);

        BudgetProgress over = BudgetProgress.of(1_200_000, 1_370_000, END, TODAY);
        assertThat(over.status()).isEqualTo(BudgetStatus.OVER_LIMIT);
        assertThat(over.remaining()).isEqualTo(-170_000L);
        assertThat(over.ratio()).isEqualByComparingTo(new BigDecimal("1.1417"));
    }

    @Test
    @DisplayName("Hạn mức 0 không chia cho 0, tỉ lệ là 0")
    void zeroLimit_ratioIsZero() {
        BudgetProgress progress = BudgetProgress.of(0, 0, END, TODAY);

        assertThat(progress.ratio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Số ngày còn lại tính tới hết kỳ và không bao giờ âm")
    void daysRemaining_neverNegative() {
        assertThat(BudgetProgress.of(1, 0, END, TODAY).daysRemaining()).isEqualTo(21);
        assertThat(BudgetProgress.of(1, 0, LocalDate.of(2026, 9, 30), TODAY).daysRemaining()).isZero();
    }
}
