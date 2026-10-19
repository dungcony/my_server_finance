package com.datn.financeapp.module.transaction.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Yêu cầu tạo mới một buổi lịch phát sinh cho giao dịch định kỳ.
 *
 * @param runDate Ngày dự kiến chạy của buổi lịch (LocalDate)
 * @param amount  Số tiền áp dụng riêng cho buổi lịch (Đơn vị: VND)
 */
public record RecTxnScheduleCreateReq(
        @NotNull(message = "Ngày chạy không được để trống.")
        LocalDate runDate,

        @NotNull(message = "Số tiền không được để trống.")
        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount
) {
}
