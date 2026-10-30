package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;

import java.time.Instant;

/**
 * Trạng thái một đơn thanh toán, dùng cho cả polling và từng dòng lịch sử của user.
 */
public record PaymentStatusRes(
        // mã đơn hàng thanh toán
        long orderCode,

        // loại giao dịch thanh toán
        PaymentType type,

        // tham chiếu gói hoặc sản phẩm được mua
        String referenceId,

        // số tiền cần thanh toán ban đầu (VND)
        long amount,

        // số tiền thực tế hệ thống đã nhận được từ người dùng (VND)
        Long amountPaid,

        // số tiền còn thiếu người dùng cần nạp nốt khi chuyển thiếu
        Long amountRemaining,

        // số tiền người dùng chuyển thừa (nếu có)
        Long excessAmount,

        // trạng thái hiện tại của đơn thanh toán
        PaymentStatus status,

        // thời điểm thanh toán thành công
        Instant paidAt,

        // thời hạn thanh toán ban đầu
        Instant expiresAt,

        // hạn chót nạp bù tiền khi thanh toán thiếu
        Instant topupDeadline,

        // thời điểm khởi tạo đơn
        Instant createdAt
) {
}
