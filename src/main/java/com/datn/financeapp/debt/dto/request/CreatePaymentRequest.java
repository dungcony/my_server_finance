package com.datn.financeapp.debt.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body {@code POST /debts/{id}/payments} (api/08 mục 5). {@code paidDate} rỗng = hôm nay,
 * {@code walletId} rỗng = dùng ví của khoản nợ.
 */
public record CreatePaymentRequest(
        @NotNull(message = "Thiếu số tiền trả.") @Positive(message = "Số tiền phải lớn hơn 0.") Long amount,
        LocalDate paidDate,
        UUID walletId,
        String note) {
}
