package com.datn.financeapp.module.pay.enums;

/**
 * Trạng thái của đơn thanh toán trong hệ thống.
 * Các trạng thái cuối cùng (terminal) không thể thay đổi: PAID, CANCELLED, EXPIRED, FORFEITED.
 */
public enum PaymentStatus {
    // Đơn vừa tạo, đang chờ người dùng quét mã thanh toán
    PENDING,

    // Đã nhận được một phần tiền nhưng chưa đủ, đang chờ người dùng chuyển bù nốt
    PARTIALLY_PAID,

    // Đã nhận đủ hoặc thừa tiền, đơn hàng được kích hoạt thành công
    PAID,

    // Đơn bị người dùng huỷ hoặc hệ thống huỷ khi tạo đơn mới thay thế
    CANCELLED,

    // Đơn hết hạn thanh toán ban đầu mà chưa nhận được đồng nào
    EXPIRED,

    // Quá hạn bù tiền mà người dùng không nạp nốt, hệ thống đóng đơn và giữ số tiền đã nhận
    FORFEITED
}
