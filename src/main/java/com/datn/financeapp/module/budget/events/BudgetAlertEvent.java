package com.datn.financeapp.module.budget.events;

import java.util.UUID;

/**
 * Sự kiện phát ra khi chi tiêu chạm ngưỡng cảnh báo ngân sách (near_limit hoặc over_limit).
 *
 * @param userId   ID người dùng sở hữu ngân sách
 * @param title    Tiêu đề thông báo cảnh báo
 * @param content  Nội dung thông báo
 * @param budgetId ID ngân sách bị ảnh hưởng
 */
public record BudgetAlertEvent(
        UUID userId,
        String title,
        String content,
        UUID budgetId
) {
}
