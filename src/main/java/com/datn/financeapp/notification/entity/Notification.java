package com.datn.financeapp.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity cho bảng {@code notifications} (V9, D-38). Hộp thư thông báo trong app — lịch sử tại
 * thời điểm phát sinh cảnh báo/nhắc nhở, KHÔNG tự cập nhật lại khi dữ liệu gốc thay đổi sau đó
 * (D-41). Theo đúng khuôn {@code Wallet.java}.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // CHECK IN ('budget_alert','debt_reminder','recurring_generated','budget_renewed','goal_completed').
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    // budget_id hoặc debt_id tuỳ type — không có FK cứng vì trỏ nhiều bảng khác nhau.
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
