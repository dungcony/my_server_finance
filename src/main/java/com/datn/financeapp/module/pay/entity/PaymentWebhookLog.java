package com.datn.financeapp.module.pay.entity;

import com.datn.financeapp.module.pay.enums.WebhookResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lần payOS gọi webhook, khớp bảng {@code payment_webhook_logs} (V18). Chỉ ghi thêm, không sửa, không xoá mềm.
 * Không lưu body gốc hay thông tin ngân hàng/tên người chuyển (dữ liệu cá nhân).
 */
@Entity
@Table(name = "payment_webhook_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** Với INVALID_*, đây là giá trị bên gửi tự khai (chưa được xác thực). */
    @Column(name = "order_code")
    private Long orderCode;

    @Column(name = "payment_link_id")
    private String paymentLinkId;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "payos_code")
    private String payosCode;

    @Column(name = "success")
    private Boolean success;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private WebhookResult result;

    @Column(name = "message")
    private String message;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "client_ip")
    private String clientIp;

    /** Số request đã gộp vào dòng này (luôn 1 với dòng thường). */
    @Builder.Default
    @Column(name = "occurrences", nullable = false)
    private int occurrences = 1;

    /** TRUE: dòng gộp các request giả mạo vượt giới hạn mỗi phút. */
    @Column(name = "aggregated", nullable = false)
    private boolean aggregated;
}
