package com.datn.financeapp.module.transaction.dto.request;

import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * Yêu cầu cập nhật thông tin một buổi lịch phát sinh.
 * Chỉ cho phép cập nhật khi buổi lịch đang ở trạng thái PENDING.
 *
 * @param runAt  Thời điểm chạy mới nếu cần dời lịch (Định dạng: UTC Instant)
 * @param amount Số tiền mới nếu cần điều chỉnh riêng cho buổi này (Đơn vị: VND)
 */
public record RecTxnScheduleUpdateReq(
        Instant runAt,

        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount
) {
}
