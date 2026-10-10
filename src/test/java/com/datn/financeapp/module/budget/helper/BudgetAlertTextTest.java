package com.datn.financeapp.module.budget.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.module.budget.enums.BudgetStatus;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetAlertTextTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("Tiền định dạng có dấu chấm ngăn nghìn và hậu tố đ")
    void formatAmount() {
        assertThat(BudgetAlertText.formatAmount(1_500_000)).isEqualTo("1.500.000 đ");
        assertThat(BudgetAlertText.formatAmount(0)).isEqualTo("0 đ");
    }

    @Test
    @DisplayName("Tiêu đề theo trạng thái: vượt thì Đã vượt, còn lại là Sắp hết")
    void title() {
        assertThat(BudgetAlertText.title(BudgetStatus.OVER_LIMIT)).isEqualTo("Đã vượt ngân sách");
        assertThat(BudgetAlertText.title(BudgetStatus.NEAR_LIMIT)).isEqualTo("Sắp hết ngân sách");
    }

    @Test
    @DisplayName("Nội dung khi vượt nêu số tiền vượt và số ngày còn lại của kỳ")
    void content_overLimit() {
        BudgetProgress progress = BudgetProgress.of(1_200_000, 1_370_000, END, TODAY);

        assertThat(BudgetAlertText.content(progress)).isEqualTo("Vượt 170.000 đ khi kỳ còn 21 ngày.");
    }

    @Test
    @DisplayName("Nội dung khi sắp chạm nêu số tiền còn lại")
    void content_nearLimit() {
        BudgetProgress progress = BudgetProgress.of(3_000_000, 2_430_000, END, TODAY);

        assertThat(BudgetAlertText.content(progress)).isEqualTo("Còn 570.000 đ cho 21 ngày còn lại của kỳ.");
    }
}
