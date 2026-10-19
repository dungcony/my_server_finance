package com.datn.financeapp.module.pay.events.publisher;

import com.datn.financeapp.module.pay.enums.PaymentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi đơn thanh toán thiếu tiền sắp đến hạn chót cần nhắc nhở người dùng.
 */
public record PaymentTopupReminderEvent(
        // người dùng nhận thông báo nhắc
        UUID userId,

        // loại thanh toán
        PaymentType type,

        // mã gói hoặc sản phẩm thanh toán
        String referenceId,

        // mã đơn hàng
        long orderCode,

        // tổng số tiền đơn hàng
        long amount,

        // số tiền đã nạp
        long amountPaid,

        // số tiền còn thiếu cần bù
        long amountRemaining,

        // thời hạn nạp bù chót
        Instant topupDeadline
) {
}
