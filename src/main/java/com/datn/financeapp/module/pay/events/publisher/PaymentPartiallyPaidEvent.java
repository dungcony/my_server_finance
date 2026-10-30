package com.datn.financeapp.module.pay.events.publisher;

import com.datn.financeapp.module.pay.enums.PaymentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi người dùng thanh toán thiếu tiền và đơn chuyển sang trạng thái PARTIALLY_PAID.
 */
public record PaymentPartiallyPaidEvent(
        // người dùng thực hiện thanh toán
        UUID userId,

        // loại thanh toán
        PaymentType type,

        // mã gói hoặc sản phẩm thanh toán
        String referenceId,

        // mã đơn hàng
        long orderCode,

        // tổng số tiền cần thanh toán
        long amount,

        // số tiền đã nhận được
        long amountPaid,

        // số tiền còn thiếu cần bù nốt
        long amountRemaining,

        // hạn chót để nạp bù tiền
        Instant topupDeadline
) {
}
