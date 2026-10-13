package com.datn.financeapp.module.notification.dto.response;

import com.datn.financeapp.module.notification.enums.NotiType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Thông tin chi tiết một thông báo.
 *
 * @param id          ID thông báo
 * @param type        Loại thông báo (VD: {@code BUDGET_ALERT}, {@code GROUP_TXN_RECORD_REQUEST}), app dựa vào để chọn icon và màn hình đích
 * @param title       Tiêu đề thông báo
 * @param content     Nội dung chi tiết thông báo
 * @param referenceId ID đối tượng chính thông báo nhắc tới (ngân sách, khoản nợ, giao dịch nhóm...), ý nghĩa tuỳ {@code type}
 * @param data        Dữ liệu phụ để app điều hướng, json phẳng, null nếu không cần; không chứa trạng thái.
 *                    VD thông báo giao dịch nhóm: {@code action} và {@code group_id}
 * @param isRead      Trạng thái đã đọc hay chưa
 * @param createdAt   Thời điểm sinh thông báo
 */
public record NotificationRes(
        UUID id,
        NotiType type,
        String title,
        String content,
        UUID referenceId,
        Map<String, Object> data,
        Boolean isRead,
        Instant createdAt) {
}
