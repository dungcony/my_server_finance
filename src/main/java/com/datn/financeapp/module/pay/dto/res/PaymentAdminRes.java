package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Một đơn thanh toán dưới góc nhìn quản trị: bổ sung người trả, mã link payOS và thông tin phạt quá hạn.
 */
public record PaymentAdminRes(
        // mã đơn hàng thanh toán
        long orderCode,

        // định danh người dùng sở hữu đơn
        UUID userId,

        // loại hàng hoá thanh toán
        PaymentType type,

        // mã tham chiếu sản phẩm
        String referenceId,

        // số tiền yêu cầu thanh toán ban đầu (VND)
        long amount,

        // số tiền thực tế payOS đã thu được (VND)
        Long amountPaid,

        // số tiền còn thiếu cần thanh toán nốt khi chuyển thiếu
        Long amountRemaining,

        // số tiền nạp dư của người dùng
        Long excessAmount,

        // trạng thái hiện tại của đơn
        PaymentStatus status,

        // mã link payOS tương ứng
        String payosLinkId,

        // thời điểm thanh toán thành công
        Instant paidAt,

        // thời hạn thanh toán ban đầu
        Instant expiresAt,

        // hạn chót nạp bù tiền khi thanh toán thiếu
        Instant topupDeadline,

        // thời điểm bị đóng đơn và giữ tiền do quá hạn bù
        Instant forfeitedAt,

        // thời điểm tạo đơn
        Instant createdAt,

        // thời điểm cập nhật trạng thái gần nhất
        Instant updatedAt
) {
}
