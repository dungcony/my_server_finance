package com.datn.financeapp.notification.dto;

import com.datn.financeapp.common.response.PageMeta;
import java.util.List;

/**
 * Khung phản hồi cho {@code GET /notifications} — {@code {success, data, pagination}} theo
 * api/00-QUY-UOC-CHUNG.md mục 4.2, cùng khuôn {@code TransactionListResponse} (không sửa
 * {@code ApiResponse} dùng chung).
 */
public record NotificationListResponse(boolean success, List<NotificationListItemResponse> data, PageMeta pagination) {

    public static NotificationListResponse of(List<NotificationListItemResponse> data, PageMeta pagination) {
        return new NotificationListResponse(true, data, pagination);
    }
}
