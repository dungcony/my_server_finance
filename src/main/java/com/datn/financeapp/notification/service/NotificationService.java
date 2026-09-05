package com.datn.financeapp.notification.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.notification.dto.response.NotificationListItemResponse;
import com.datn.financeapp.notification.dto.response.NotificationListResponse;
import com.datn.financeapp.notification.entity.Notification;
import com.datn.financeapp.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic đọc/đánh dấu đã đọc thông báo (api/11-THONG-BAO.md mục 1-2). Mọi truy vấn chọn/
 * sửa MỘT thông báo theo id đi qua {@link NotificationRepository} với điều kiện quyền D-27 sẵn
 * có trong query — không có quyền trả {@code NOT_FOUND} (404), không phải 403 (T-04-02).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public NotificationListResponse list(UUID userId, Boolean isRead, PageRequestParams pageParams) {
        int limit = pageParams.pageSize();
        int offset = (pageParams.page() - 1) * pageParams.pageSize();

        List<Notification> notifications = notificationRepository.findAllForUser(userId, isRead, limit, offset);
        long totalItems = notificationRepository.countForUser(userId, isRead);
        int totalPages = (int) Math.ceil((double) totalItems / pageParams.pageSize());

        List<NotificationListItemResponse> data = notifications.stream().map(this::toResponse).toList();
        PageMeta pagination = new PageMeta(pageParams.page(), pageParams.pageSize(), totalItems, totalPages);

        return NotificationListResponse.of(data, pagination);
    }

    /**
     * api/11-THONG-BAO.md mục 2 — idempotent: {@code UPDATE ... WHERE} không đổi gì nếu gọi lại
     * trên thông báo đã đọc, vẫn trả 200 bình thường (không cần kiểm tra trạng thái trước).
     */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        notificationRepository
                .findByIdForUser(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thông báo."));

        notificationRepository.markRead(notificationId, userId);
    }

    private NotificationListItemResponse toResponse(Notification notification) {
        return new NotificationListItemResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getReferenceId(),
                notification.getIsRead(),
                notification.getCreatedAt());
    }
}
