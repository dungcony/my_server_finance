package com.datn.financeapp.module.budget.dto.response;

import com.datn.financeapp.module.budget.enums.BudgetStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cảnh báo cho một ngân sách đã từ 80% hạn mức. Cũng là dữ liệu để module báo cáo dựng mục
 * "ngân sách cần chú ý".
 *
 * @param budgetId ID ngân sách
 * @param category tên danh mục
 * @param severity {@code critical} (đã vượt) | {@code alert} (từ 80%)
 * @param ratio    tỉ lệ đã chi trên hạn mức
 * @param status   trạng thái theo tỉ lệ
 * @param title    tiêu đề cảnh báo
 * @param content  nội dung cảnh báo
 */
public record BudgetAlertRes(
        UUID budgetId,
        String category,
        String severity,
        BigDecimal ratio,
        BudgetStatus status,
        String title,
        String content) {
}
