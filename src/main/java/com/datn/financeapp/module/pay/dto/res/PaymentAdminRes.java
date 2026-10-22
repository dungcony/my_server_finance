package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Một đơn thanh toán dưới góc nhìn quản trị: thêm người trả và mã link payOS.
 */
public record PaymentAdminRes(
        long orderCode,
        UUID userId,
        PaymentType type,
        String referenceId,
        long amount,
        Long amountPaid,
        Long excessAmount,
        PaymentStatus status,
        String payosLinkId,
        Instant paidAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
