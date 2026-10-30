package com.datn.financeapp.module.notification.dto.response;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;

/**
 * Phản hồi danh sách thông báo người dùng có phân trang theo chuẩn Rule 2.4.
 *
 * @param items Danh sách các phần tử thông báo
 * @param meta  Metadata phân trang
 */
public record NotificationListResponse(List<NotificationRes> items, PageMeta meta) {

    /**
     * Khởi tạo đối tượng phản hồi danh sách thông báo.
     *
     * @param items Danh sách thông báo
     * @param meta  Dữ liệu phân trang
     * @return Phản hồi danh sách thông báo
     */
    public static NotificationListResponse of(List<NotificationRes> items, PageMeta meta) {
        return new NotificationListResponse(items, meta);
    }
}
