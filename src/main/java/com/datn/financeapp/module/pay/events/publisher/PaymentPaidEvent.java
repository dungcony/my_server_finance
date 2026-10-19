package com.datn.financeapp.module.pay.events.publisher;

import com.datn.financeapp.module.pay.enums.PaymentType;

import java.util.UUID;

/**
 * Phát khi đơn thanh toán chuyển sang PAID. Module sở hữu loại hàng hoá nghe bằng {@code @EventListener}
 * (đồng bộ, cùng transaction với webhook) để giao hàng; lỗi ở listener làm rollback cả việc ghi PAID.
 *
 * @param userId      người trả tiền
 * @param type        loại hàng hoá
 * @param referenceId thứ được mua
 * @param orderCode   mã đơn payOS
 */
public record PaymentPaidEvent(UUID userId, PaymentType type, String referenceId, long orderCode) {
}
