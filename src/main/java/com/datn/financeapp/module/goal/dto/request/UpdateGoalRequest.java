package com.datn.financeapp.module.goal.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu cập nhật thông tin mục tiêu tiết kiệm.
 *
 * @param name         Tên mục tiêu mới (tối đa 100 ký tự)
 * @param targetAmount Số tiền mục tiêu mới (Đơn vị: VND)
 * @param targetDate   Ngày dự kiến hoàn thành mới
 * @param iconId       ID biểu tượng đại diện mới
 * @param status       Trạng thái mục tiêu (chỉ chấp nhận {@code in_progress} hoặc {@code cancelled})
 */
public record UpdateGoalRequest(
        @Size(min = 1, max = 100, message = "Tên mục tiêu phải từ 1 đến 100 ký tự.")
        String name,

        @Positive(message = "Số tiền mục tiêu phải lớn hơn 0.")
        Long targetAmount,

        LocalDate targetDate,
        UUID iconId,
        String status) {
}
