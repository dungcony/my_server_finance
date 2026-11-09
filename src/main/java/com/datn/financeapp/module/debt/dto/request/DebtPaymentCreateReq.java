package com.datn.financeapp.module.debt.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu ghi nhận đợt thanh toán (thu/trả nợ).
 *
 * @param amount   Số tiền thanh toán (Đơn vị: VND)
 * @param paidDate Ngày thanh toán (mặc định: ngày hiện tại)
 * @param note     Ghi chú bổ sung
 */
public record DebtPaymentCreateReq(
        @NotNull(message = "Thiếu số tiền trả.")
        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,

        @PastOrPresent(message = "ngày trả tiền k được ở tương lai")
        LocalDate paidDate,
        String note) {
}
