package com.datn.financeapp.module.notification.service.impl;

import com.datn.financeapp.module.notification.service.NotificationService;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.module.notification.dto.response.NotificationRes;
import com.datn.financeapp.module.notification.dto.response.NotificationListResponse;
import com.datn.financeapp.module.notification.entity.Notification;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.mapper.NotificationMapper;
import com.datn.financeapp.module.notification.repository.NotificationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai nghiệp vụ đọc, tạo và quản lý thông báo người dùng (api/11-THONG-BAO.md).
 *
 * @see NotificationService
 * @see NotificationRepository
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public NotificationListResponse list(UUID userId, Boolean isRead, PageRequestParams pageParams) {
        int limit = pageParams.pageSize();
        int offset = (pageParams.page() - 1) * pageParams.pageSize();

        List<Notification> notifications = notificationRepository.findAllForUser(userId, isRead, limit, offset);
        long totalItems = notificationRepository.countForUser(userId, isRead);
        int totalPages = (int) Math.ceil((double) totalItems / pageParams.pageSize());

        List<NotificationRes> data =
                notifications.stream().map(notificationMapper::toRes).toList();
        PageMeta pagination = new PageMeta(pageParams.page(), pageParams.pageSize(), totalItems, totalPages);

        return NotificationListResponse.of(data, pagination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        notificationRepository
                .findByIdForUser(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thông báo."));

        notificationRepository.markRead(notificationId, userId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Thao tác chống trùng lặp được xử lý tại tầng CSDL bằng {@code ON CONFLICT DO NOTHING}.
     * </p>
     */
    @Override
    @Transactional
    public void createBudgetAlert(UUID userId, String title, String content, UUID budgetId) {
        notificationRepository.insertBudgetAlertIfNotExists(userId, title, content, budgetId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Thao tác chống trùng lặp được xử lý tại tầng CSDL bằng {@code ON CONFLICT DO NOTHING}.
     * </p>
     */
    @Override
    @Transactional
    public void createDebtReminder(UUID userId, String title, String content, UUID debtId) {
        notificationRepository.insertDebtReminderIfNotExists(userId, title, content, debtId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void createNotification(
            UUID userId, NotiType type, String title, String content, UUID referenceId) {
        createNotification(userId, type, title, content, referenceId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void createNotification(
            UUID userId, NotiType type, String title, String content, UUID referenceId, Map<String, Object> data) {
        notificationRepository.save(Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .referenceId(referenceId)
                .data(data)
                .isRead(false)
                .createdAt(Instant.now())
                .build());
    }
}
