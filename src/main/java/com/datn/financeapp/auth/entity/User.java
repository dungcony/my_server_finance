package com.datn.financeapp.auth.entity;

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
 * Entity khớp bảng {@code users} (V1__nen_tang.sql).
 * Id được set bằng {@link UUID#randomUUID()} ở tầng Service trước khi save,
 * không dựa vào {@code DEFAULT gen_random_uuid()} của cột để tránh Hibernate
 * phải đoán chiến lược sinh id.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private UUID id;

    /** CHECK (email = lower(email)) ở DB — Service phải lowercase trước khi lưu. */
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Tên hiển thị. Đổi tên từ {@code full_name} ở V12 — dự án chỉ còn MỘT trường tên,
     * dùng cả trong ứng dụng lẫn trong nhóm gia đình. Nhập tự do: tiếng Việt có dấu,
     * chữ hoa, khoảng trắng đều hợp lệ; KHÔNG bắt duy nhất, KHÔNG dùng để đăng nhập.
     */
    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    /** CHECK (plan IN ('free','premium')) — DB có DEFAULT 'free'. */
    @Column(name = "plan", nullable = false)
    private String plan;

    /**
     * Vai trò cấp hệ thống ({@code USER} / {@code ADMIN}) — thêm ở V12. Độc lập hoàn toàn với
     * {@code group_members.role}: ADMIN hệ thống không có quyền gì trong nhóm gia đình.
     */
    @Builder.Default
    @Column(name = "role", nullable = false)
    private String role = "USER";

    /**
     * Đã xác thực email chưa. Tài khoản đăng ký bằng email nhận {@code false} (luồng xác thực
     * chưa làm — prd/01 mục 11), tài khoản Google nhận {@code true}. Backend KHÔNG chặn đăng
     * nhập theo cột này ở đợt này, chỉ trả ra cho app biết.
     */
    @Builder.Default
    @Column(name = "is_confirm", nullable = false)
    private boolean isConfirm = false;

    /**
     * ADMIN khoá tài khoản vĩnh viễn → đăng nhập trả {@code ACCOUNT_BLOCKED}. ĐỪNG NHẦM với
     * khoá tạm 15 phút sau 5 lần sai mật khẩu ({@code ACCOUNT_LOCKED}, tính từ
     * {@code login_attempts}).
     */
    @Builder.Default
    @Column(name = "is_blocked", nullable = false)
    private boolean isBlocked = false;

    /**
     * Người dùng tự xoá tài khoản — xoá MỀM. Với thế giới bên ngoài, tài khoản này coi như
     * không tồn tại: mọi cửa vào trả đúng lỗi như thể email chưa từng đăng ký.
     */
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
