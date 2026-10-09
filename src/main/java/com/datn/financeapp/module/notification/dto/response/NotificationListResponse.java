package com.datn.financeapp.module.notification.dto.response;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;

/**
 * Phản hồi danh sách thông báo người dùng có phân trang.
 *
 * @param success    Trạng thái phản hồi (luôn là true)
 * @param data       Danh sách các phần tử thông báo
 * @param pagination Dữ liệu phân trang
 */
public record NotificationListResponse(boolean success, List<NotificationListItemResponse> data, PageMeta pagination) {

    /**
     * Khởi tạo đối tượng phản hồi danh sách thông báo.
     *
     * @param data       Danh sách thông báo
     * @param pagination Dữ liệu phân trang
     * @return Phản hồi danh sách thông báo
     */
    public static NotificationListResponse of(List<NotificationListItemResponse> data, PageMeta pagination) {
        return new NotificationListResponse(true, data, pagination);
    }
}
