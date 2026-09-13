package com.datn.financeapp.notification.service.impl;

import com.datn.financeapp.notification.service.NotificationService;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.notification.dto.response.NotificationListItemResponse;
import com.datn.financeapp.notification.dto.response.NotificationListResponse;
import com.datn.financeapp.notification.entity.Notification;
import com.datn.financeapp.notification.mapper.NotificationMapper;
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
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public NotificationListResponse list(UUID userId, Boolean isRead, PageRequestParams pageParams) {
        int limit = pageParams.pageSize();
        int offset = (pageParams.page() - 1) * pageParams.pageSize();

        List<Notification> notifications = notificationRepository.findAllForUser(userId, isRead, limit, offset);
        long totalItems = notificationRepository.countForUser(userId, isRead);
        int totalPages = (int) Math.ceil((double) totalItems / pageParams.pageSize());

        List<NotificationListItemResponse> data =
                notifications.stream().map(notificationMapper::toListItemResponse).toList();
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

    /**
     * Cảnh báo ngân sách chạm/vượt ngưỡng. Chống trùng ở tầng CSDL bằng
     * {@code ON CONFLICT DO NOTHING} trên khoá (người dùng, ngân sách, ngày, loại) — KHÔNG kiểm
     * tra tồn tại trước ở Java: nhập hàng loạt 50 dòng bắn 50 sự kiện, kiểm ở Java sẽ có tranh
     * chấp giữa các sự kiện xử lý sát nhau và vẫn lọt bản ghi trùng.
     *
     * <p>Gọi từ {@code budget/} qua service này thay vì đụng thẳng repository (quy tắc 11).
     */
    @Transactional
    public void createBudgetAlert(UUID userId, String title, String content, UUID budgetId) {
        notificationRepository.insertBudgetAlertIfNotExists(userId, title, content, budgetId);
    }

    /**
     * Nhắc khoản nợ tới hạn. Cũng chống trùng ở tầng CSDL: mốc ngày cố định chỉ đảm bảo job gọi
     * tới một lần MỖI LẦN CHẠY, không đảm bảo job chỉ chạy một lần mỗi ngày — deploy lại, retry
     * sau lỗi, hay chạy hai instance đều sinh thông báo y hệt nhau.
     */
    @Transactional
    public void createDebtReminder(UUID userId, String title, String content, UUID debtId) {
        notificationRepository.insertDebtReminderIfNotExists(userId, title, content, debtId);
    }

    /**
     * Thông báo không cần chống trùng — dùng cho các sự kiện tự nó đã chỉ xảy ra một lần, như
     * ngân sách chuyển sang kỳ mới.
     */
    @Transactional
    public void createNotification(
            UUID userId, String type, String title, String content, UUID referenceId) {
        notificationRepository.insertGenericNotification(userId, type, title, content, referenceId);
    }
}
