package com.datn.financeapp.module.pay.events.publisher;

import com.datn.financeapp.module.pay.enums.PaymentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi đơn thanh toán thiếu tiền bị quá hạn bù, hệ thống đóng đơn và giữ lại số tiền đã nhận.
 */
public record PaymentForfeitedEvent(
        // người dùng sở hữu đơn bị đóng
        UUID userId,

        // loại thanh toán
        PaymentType type,

        // mã gói hoặc sản phẩm
        String referenceId,

        // mã đơn hàng
        long orderCode,

        // tổng số tiền ban đầu của đơn
        long amount,

        // số tiền người dùng đã nạp và bị giữ lại
        long amountPaid,

        // thời điểm đơn bị đóng vĩnh viễn
        Instant forfeitedAt
) {
}
