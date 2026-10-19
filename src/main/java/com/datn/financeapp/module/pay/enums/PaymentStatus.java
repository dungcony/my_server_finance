package com.datn.financeapp.module.pay.enums;

/**
 * Trạng thái đơn thanh toán: chỉ {@link #PENDING} mới chuyển sang các trạng thái còn lại.
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    CANCELLED,
    EXPIRED
}
