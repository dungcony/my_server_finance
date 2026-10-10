package com.datn.financeapp.module.notification.service;

import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.module.notification.dto.response.NotificationListResponse;
import com.datn.financeapp.module.notification.enums.NotiType;

import java.util.Map;
import java.util.UUID;

/**
 * Service quản lý thông báo, đánh dấu đã đọc và ghi nhận thông báo mới cho người dùng.
 * <p>
 * <b>Các hàm chính:</b>
 * <ul>
 *   <li>{@link #list(UUID, Boolean, PageRequestParams)}: Lấy danh sách thông báo phân trang.</li>
 *   <li>{@link #markRead(UUID, UUID)}: Đánh dấu một thông báo là đã đọc (null = đánh dấu tất cả).</li>
 *   <li>{@link #createBudgetAlert(UUID, String, String, UUID)}: Tạo thông báo cảnh báo ngân sách.</li>
 *   <li>{@link #createDebtReminder(UUID, String, String, UUID)}: Tạo thông báo nhắc nợ đến hạn.</li>
 *   <li>{@link #createNotification(UUID, NotiType, String, String, UUID)}: Tạo thông báo chung hệ thống.</li>
 *   <li>{@link #createNotification(UUID, NotiType, String, String, UUID, Map)}: Tạo thông báo chung kèm dữ liệu phụ để app điều hướng.</li>
 * </ul>
 * </p>
 */
public interface NotificationService {

    /**
     * Truy vấn danh sách thông báo của người dùng có phân trang.
     *
     * @param userId     ID người dùng
     * @param isRead     Trạng thái đọc ({@code true} đã đọc, {@code false} chưa đọc, null = tất cả)
     * @param pageParams Tham số phân trang (trang hiện tại, số lượng trên mỗi trang)
     * @return Danh sách thông báo phân trang kèm số lượng chưa đọc
     */
    NotificationListResponse list(UUID userId, Boolean isRead, PageRequestParams pageParams);

    /**
     * Đánh dấu thông báo là đã đọc.
     *
     * @param userId         ID người dùng
     * @param notificationId ID thông báo cần đánh dấu (nếu null = đánh dấu tất cả chưa đọc)
     */
    void markRead(UUID userId, UUID notificationId);

    /**
     * Tạo thông báo cảnh báo ngân sách chi tiêu.
     *
     * @param userId   ID người dùng
     * @param title    Tiêu đề thông báo
     * @param content  Nội dung thông báo
     * @param budgetId ID ngân sách liên quan
     */
    void createBudgetAlert(UUID userId, String title, String content, UUID budgetId);

    /**
     * Tạo thông báo nhắc nợ đến hạn/quá hạn.
     *
     * @param userId  ID người dùng
     * @param title   Tiêu đề thông báo
     * @param content Nội dung thông báo
     * @param debtId  ID khoản nợ liên quan
     */
    void createDebtReminder(UUID userId, String title, String content, UUID debtId);

    /**
     * Tạo một thông báo mới trong hệ thống.
     *
     * @param userId      ID người dùng nhận thông báo
     * @param type        Loại thông báo
     * @param title       Tiêu đề thông báo
     * @param content     Nội dung chi tiết thông báo
     * @param referenceId ID đối tượng tham chiếu (ngân sách, khoản nợ, giao dịch định kỳ...)
     */
    void createNotification(UUID userId, NotiType type, String title, String content, UUID referenceId);

    /**
     * Tạo một thông báo mới kèm dữ liệu phụ để app điều hướng.
     *
     * @param userId      ID người dùng nhận thông báo
     * @param type        Loại thông báo
     * @param title       Tiêu đề thông báo
     * @param content     Nội dung chi tiết thông báo
     * @param referenceId ID đối tượng tham chiếu (ngân sách, khoản nợ, giao dịch nhóm...)
     * @param data        Dữ liệu phụ dạng json phẳng, null nếu loại thông báo không cần; không chứa trạng thái
     */
    void createNotification(
            UUID userId, NotiType type, String title, String content, UUID referenceId, Map<String, Object> data);
}
