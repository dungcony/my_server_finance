package com.datn.financeapp.module.debt.dto.request;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record DebtPaymentUpdateReq(
        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,
        @PastOrPresent(message = "ngày trả tiền k được ở tương lai")
        LocalDate paidDate,
        String note) {
}
