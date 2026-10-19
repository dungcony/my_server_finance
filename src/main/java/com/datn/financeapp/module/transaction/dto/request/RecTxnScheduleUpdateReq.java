package com.datn.financeapp.module.transaction.dto.request;

import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Yêu cầu cập nhật thông tin một buổi lịch phát sinh.
 * Chỉ cho phép cập nhật khi buổi lịch đang ở trạng thái PENDING.
 *
 * @param runDate Ngày chạy mới nếu cần dời lịch (LocalDate)
 * @param amount  Số tiền mới nếu cần điều chỉnh riêng cho buổi này (Đơn vị: VND)
 */
public record RecTxnScheduleUpdateReq(
        LocalDate runDate,

        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount
) {
}
