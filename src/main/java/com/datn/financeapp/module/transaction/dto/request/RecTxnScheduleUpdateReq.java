package com.datn.financeapp.module.transaction.dto.request;

import com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * Yêu cầu cập nhật thông tin một buổi lịch phát sinh.
 *
 * @param runAt  Thời điểm chạy mới nếu cần dời lịch (Định dạng: UTC Instant)
 * @param amount Số tiền mới nếu cần điều chỉnh riêng cho buổi này (Đơn vị: VND)
 * @param status Trạng thái mới của buổi lịch (ví dụ: SKIPPED để bỏ qua, PENDING để khôi phục)
 */
public record RecTxnScheduleUpdateReq(
        Instant runAt,

        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,

        RecTxnScheduleStatus status
) {
}
