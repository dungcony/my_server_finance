package com.datn.financeapp.module.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin chi tiết một thông báo trong danh sách.
 *
 * @param id          ID thông báo
 * @param type        Loại thông báo (VD: {@code budget_alert}, {@code debt_reminder})
 * @param title       Tiêu đề thông báo
 * @param content     Nội dung chi tiết thông báo
 * @param referenceId ID đối tượng liên quan (ngân sách, khoản nợ...)
 * @param isRead      Trạng thái đã đọc hay chưa
 * @param createdAt   Thời điểm sinh thông báo
 */
public record NotificationListItemResponse(
        UUID id,
        String type,
        String title,
        String content,
        UUID referenceId,
        Boolean isRead,
        Instant createdAt) {
}
