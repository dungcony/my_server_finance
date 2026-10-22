package com.datn.financeapp.module.goal.dto.request;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO nhận tiêu chí lọc danh sách mục tiêu tiết kiệm dành cho Admin.
 *
 * @param userId         ID người dùng cần lọc (tùy chọn)
 * @param status         Trạng thái mục tiêu: in_progress, completed, cancelled (tùy chọn)
 * @param name           Từ khóa tìm kiếm theo tên mục tiêu (tùy chọn)
 * @param targetDateFrom Ngày hạn mục tiêu từ ngày (tùy chọn)
 * @param targetDateTo   Ngày hạn mục tiêu đến ngày (tùy chọn)
 */
public record GoalFilterRequest(
        UUID userId,
        String status,
        String name,
        LocalDate targetDateFrom,
        LocalDate targetDateTo) {
}
