package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;

import java.time.Instant;

/**
 * Trạng thái một đơn thanh toán, dùng cho cả polling và từng dòng lịch sử của user.
 */
public record PaymentStatusRes(
        long orderCode,
        PaymentType type,
        String referenceId,
        long amount,
        Long amountPaid,
        Long excessAmount,
        PaymentStatus status,
        Instant paidAt,
        Instant expiresAt,
        Instant createdAt
) {
}
