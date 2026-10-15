package com.datn.financeapp.module.transaction.dto.request;

import com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * Yêu cầu tạo mới một buổi lịch phát sinh cho giao dịch định kỳ.
 *
 * @param runAt  Thời điểm dự kiến chạy của buổi lịch (Định dạng: UTC Instant)
 * @param amount Số tiền áp dụng riêng cho buổi lịch (Đơn vị: VND)
 * @param status Trạng thái ban đầu của buổi lịch (Mặc định: PENDING nếu để trống)
 */
public record RecTxnScheduleCreateReq(
        @NotNull(message = "Thời gian chạy không được để trống.")
        Instant runAt,

        @NotNull(message = "Số tiền không được để trống.")
        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,

        RecTxnScheduleStatus status
) {
}
