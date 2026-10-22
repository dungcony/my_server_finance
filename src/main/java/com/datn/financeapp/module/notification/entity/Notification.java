package com.datn.financeapp.module.notification.entity;

import com.datn.financeapp.module.notification.enums.NotiType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /**
     * Loại thông báo; app dựa vào đây để chọn icon và màn hình đích. Lưu tên hằng (VD {@code GROUP_TXN_RECORD_REQUEST}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotiType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    /**
     * Id đối tượng chính mà thông báo nhắc tới, ý nghĩa tuỳ {@code type}: ngân sách (BUDGET_*),
     * khoản nợ (DEBT_REMINDER), giao dịch nhóm (GROUP_TXN_*). Không có khoá ngoại vì trỏ nhiều bảng.
     */
    @Column(name = "reference_id")
    private UUID referenceId;

    /**
     * Dữ liệu phụ để app điều hướng, là json phẳng, null nếu loại thông báo không cần. Không chứa trạng thái
     * (thông báo là snapshot). Thông báo giao dịch nhóm (GROUP_TXN_*): {@code action} = việc người nhận cần làm,
     * {@code group_id} = nhóm chứa giao dịch ({@code reference_id}) để app gọi GET chi tiết giao dịch.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data")
    private Map<String, Object> data;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
