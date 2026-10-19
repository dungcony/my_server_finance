package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.module.pay.enums.PaymentStatus;

import java.time.Instant;

/**
 * Kết quả tạo đơn thanh toán. Khi user còn đơn chờ thì trả lại đúng đơn đó nên {@code referenceId}
 * có thể khác thứ vừa yêu cầu.
 */
public record PaymentCreateRes(
        long orderCode,
        String referenceId,
        long amount,
        PaymentStatus status,
        String checkoutUrl,
        Instant expiresAt
) {
}
