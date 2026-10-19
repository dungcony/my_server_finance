package com.datn.financeapp.module.pay.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Đơn thanh toán payOS, khớp bảng {@code payments}.
 * Id được set bằng {@link UUID#randomUUID()} ở tầng Service trước khi save.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends AssignedIdEntity {

    @Id
    private UUID id;

    // mã đơn duy nhất gửi cho payOS, lấy từ sequence
    @Column(name = "order_code", nullable = false, updatable = false)
    private Long orderCode;

    // người thực hiện giao dịch thanh toán
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // loại giao dịch thanh toán (ví dụ PREMIUM_UPGRADE)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private PaymentType type;

    // tham chiếu nghiệp vụ cụ thể (ví dụ mã gói PREMIUM_1M)
    @Column(name = "reference_id")
    private String referenceId;

    // số tiền yêu cầu thanh toán ban đầu (VND)
    @Column(name = "amount", nullable = false, updatable = false)
    private Long amount;

    // số tiền thực tế payOS đã thu nhận được (VND)
    @Column(name = "amount_paid")
    private Long amountPaid;

    // trạng thái xử lý hiện tại của đơn hàng
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    // mã định danh payment link trả về từ payOS
    @Column(name = "payos_link_id")
    private String payosLinkId;

    // thời hạn thanh toán ban đầu của link payOS
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // thời điểm đơn hàng được thanh toán đủ tiền
    @Column(name = "paid_at")
    private Instant paidAt;

    // hạn chót người dùng phải nạp nốt số tiền còn thiếu khi chuyển thiếu
    @Column(name = "topup_deadline")
    private Instant topupDeadline;

    // thời điểm hệ thống đã phát thông báo nhắc hạn nạp tiền (tránh gửi trùng lặp)
    @Column(name = "topup_reminded_at")
    private Instant topupRemindedAt;

    // thời điểm đơn bị đóng vĩnh viễn và giữ tiền do quá hạn nạp bù
    @Column(name = "forfeited_at")
    private Instant forfeitedAt;
}
