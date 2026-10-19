package com.datn.financeapp.module.pay.dto.req;

import com.datn.financeapp.module.pay.enums.PaymentType;

import java.util.UUID;

/**
 * Yêu cầu tạo đơn thanh toán, chỉ dùng nội bộ giữa module sở hữu hàng hoá và {@code pay}
 * (không nhận từ HTTP). Số tiền do module gọi tính, client không bao giờ gửi.
 *
 * @param userId      người mua
 * @param type        loại hàng hoá
 * @param referenceId thứ được mua
 * @param amount      số tiền VND
 */
public record PaymentCreateReq(UUID userId, PaymentType type, String referenceId, long amount) {

    public PaymentCreateReq {
        if (userId == null) {
            throw new IllegalArgumentException("userId không được để trống");
        }
        if (type == null) {
            throw new IllegalArgumentException("type không được để trống");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("referenceId không được để trống");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount phải lớn hơn 0");
        }
    }
}
