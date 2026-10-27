package com.datn.financeapp.module.goal.dto.request;

import com.datn.financeapp.module.goal.enums.SavingGoalStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Yêu cầu cập nhật thông tin mục tiêu tiết kiệm.
 *
 * @param name         Tên mục tiêu mới (tối đa 100 ký tự)
 * @param targetAmount Số tiền mục tiêu mới (Đơn vị: VND)
 * @param targetDate   Ngày dự kiến hoàn thành mới
 * @param iconCode     Mã biểu tượng đại diện mới
 * @param note         Ghi chú nơi cất giữ tiền mới (tối đa 255 ký tự)
 * @param status       Trạng thái mục tiêu (chỉ chấp nhận {@link SavingGoalStatus#IN_PROGRESS} hoặc {@link SavingGoalStatus#CANCELLED})
 */
public record SavingGoalUpdateRequest(
        @Size(min = 1, max = 100, message = "Tên mục tiêu phải từ 1 đến 100 ký tự.")
        String name,

        @Positive(message = "Số tiền mục tiêu phải lớn hơn 0.")
        Long targetAmount,

        LocalDate targetDate,

        @Size(max = 50, message = "Mã biểu tượng tối đa 50 ký tự.")
        String iconCode,

        @Size(max = 255, message = "Ghi chú nơi giữ tiền tối đa 255 ký tự.")
        String note,

        SavingGoalStatus status) {
}
