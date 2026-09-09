package com.datn.financeapp.notification.service;

import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.notification.dto.response.NotificationListResponse;
import java.util.UUID;

// Public API của module Notification (Đọc và quản lý thông báo người dùng).
public interface NotificationService {

    NotificationListResponse list(UUID userId, Boolean isRead, PageRequestParams pageParams);

    void markRead(UUID userId, UUID notificationId);

    void createBudgetAlert(UUID userId, String title, String content, UUID budgetId);

    void createDebtReminder(UUID userId, String title, String content, UUID debtId);

    void createNotification(UUID userId, String type, String title, String content, UUID referenceId);
}
