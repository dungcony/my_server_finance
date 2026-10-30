package com.datn.financeapp.module.pay.enums;

import java.util.Set;

/**
 * Kết quả của MỘT lần payOS gọi webhook (không phải trạng thái đơn). Dùng để phân loại trên dashboard admin.
 */
public enum WebhookResult {
    /** Đủ tiền, đơn chuyển PAID và đã giao hàng. */
    PROCESSED(false),
    /** payOS gửi lại cho đơn đã PAID, bỏ qua. */
    ALREADY_PAID(false),
    /** payOS báo không thành công (success=false hoặc code khác 00), bỏ qua. */
    IGNORED_NOT_SUCCESS(false),
    /** Không có dữ liệu đơn, hoặc không phải đơn của hệ thống (VD webhook thử khi đăng ký URL). */
    IGNORED_EMPTY(false),
    /** Có tiền về cho order_code thuộc dải của hệ thống nhưng không có đơn (link mồ côi?). */
    ORDER_NOT_FOUND(true),
    /** payment_link_id không khớp link đã lưu của đơn. */
    LINK_MISMATCH(true),
    /** Đơn đã CANCELLED/EXPIRED mà vẫn nhận tiền: bất biến bị vỡ, không giao hàng. */
    ORDER_CLOSED(true),
    /** Số tiền thu nhỏ hơn số đơn, không giao hàng. */
    UNDERPAID(true),
    /** Body không phải JSON hợp lệ. */
    INVALID_BODY(true),
    /** Sai chữ ký hoặc dữ liệu bị sửa. */
    INVALID_SIGNATURE(true),
    /** Lỗi bất ngờ khi xử lý (rollback, payOS chưa cấu hình...). */
    ERROR(true);

    private final boolean failure;

    WebhookResult(boolean failure) {
        this.failure = failure;
    }

    public boolean isFailure() {
        return failure;
    }

    /** Hai loại do bên ngoài gửi bậy, dễ bị spam nên có giới hạn ghi log mỗi phút. */
    public boolean isSpamProne() {
        return this == INVALID_BODY || this == INVALID_SIGNATURE;
    }

    public static Set<WebhookResult> failures() {
        return Set.of(INVALID_BODY, INVALID_SIGNATURE, ORDER_NOT_FOUND, LINK_MISMATCH, ORDER_CLOSED, UNDERPAID, ERROR);
    }

    /** Kết quả cho thấy webhook đã tới và xác nhận đơn đã trả. */
    public static Set<WebhookResult> confirmations() {
        return Set.of(PROCESSED, ALREADY_PAID);
    }
}
