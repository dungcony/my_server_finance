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
 * Đơn thanh toán payOS, khớp bảng {@code payments} (V16__payments_and_premium.sql).
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

    /** Mã đơn gửi payOS, lấy từ sequence. */
    @Column(name = "order_code", nullable = false, updatable = false)
    private Long orderCode;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private PaymentType type;

    /** Thứ được mua, do module sở hữu loại hàng hoá diễn giải (VD: mã gói premium). */
    @Column(name = "reference_id")
    private String referenceId;

    /** Số tiền VND do server tính. */
    @Column(name = "amount", nullable = false, updatable = false)
    private Long amount;

    /** Số tiền VND thực tế payOS đã thu (chỉ ghi khi đơn chuyển PAID). */
    @Column(name = "amount_paid")
    private Long amountPaid;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "payos_link_id")
    private String payosLinkId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;
}
