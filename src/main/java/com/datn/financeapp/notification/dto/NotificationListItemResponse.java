package com.datn.financeapp.notification.dto;

import java.time.Instant;
import java.util.UUID;

/** Một thông báo trong danh sách trả về từ {@code GET /notifications} (api/11-THONG-BAO.md mục 1). */
public record NotificationListItemResponse(
        UUID id,
        String type,
        String title,
        String content,
        UUID referenceId,
        Boolean isRead,
        Instant createdAt) {}
