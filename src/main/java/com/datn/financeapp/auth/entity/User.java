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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
