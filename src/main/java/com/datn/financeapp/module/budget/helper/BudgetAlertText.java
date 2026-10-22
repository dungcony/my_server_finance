package com.datn.financeapp.module.budget.helper;

import com.datn.financeapp.module.budget.enums.BudgetStatus;

/**
 * Câu chữ cảnh báo ngân sách, dùng chung cho thông báo, danh sách cảnh báo và response giao dịch.
 * <p>
 * <b>Các hàm:</b> {@link #title(BudgetStatus)} tiêu đề theo trạng thái; {@link #content(BudgetProgress)} nội dung
 * kèm số tiền và số ngày còn lại; {@link #formatAmount(long)} định dạng tiền VND.
 * </p>
 */
public final class BudgetAlertText {

    private BudgetAlertText() {
    }

    public static String title(BudgetStatus status) {
        return status == BudgetStatus.OVER_LIMIT ? "Đã vượt ngân sách" : "Sắp hết ngân sách";
    }

    public static String content(BudgetProgress progress) {
        if (progress.status() == BudgetStatus.OVER_LIMIT) {
            return "Vượt " + formatAmount(-progress.remaining()) + " khi kỳ còn " + progress.daysRemaining() + " ngày.";
        }
        return "Còn " + formatAmount(progress.remaining()) + " cho " + progress.daysRemaining() + " ngày còn lại của kỳ.";
    }

    /**
     * Định dạng số tiền nguyên với dấu chấm ngăn nghìn, ví dụ {@code 1.500.000 đ}.
     */
    public static String formatAmount(long amount) {
        return String.format("%,d", amount).replace(',', '.') + " đ";
    }
}
