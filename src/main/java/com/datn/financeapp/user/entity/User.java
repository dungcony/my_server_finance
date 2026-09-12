package com.datn.financeapp.user.entity;

import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity khớp bảng {@code users} (V1__nen_tang.sql).
 * Id được set bằng {@link UUID#randomUUID()} ở tầng Service trước khi save.
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

    // CHECK (email = lower(email)) ở DB — Service phải lowercase trước khi lưu.
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    // NULL với tài khoản Google thuần — chưa từng đặt mật khẩu.
    @Column(name = "password_hash")
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    // Trường {@code sub} của {@code id_token} Google.
    @Column(name = "google_id", unique = true)
    private String googleId;

    @Builder.Default
    @Column(name = "plan", nullable = false)
    private UserPlan plan = UserPlan.FREE;

    @Builder.Default
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    // Người dùng tự xoá tài khoản — xoá MỀM.
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<UserRole> userRoles = new HashSet<>();

    public boolean isBlocked() {
        return this.status == UserStatus.BLOCKED;
    }

    public void setBlocked(boolean blocked) {
        this.status = blocked ? UserStatus.BLOCKED : UserStatus.ACTIVE;
    }

    public boolean isConfirm() {
        return this.status == UserStatus.ACTIVE;
    }

    public void setConfirm(boolean confirm) {
        if (confirm) {
            this.status = UserStatus.ACTIVE;
        }
    }
}
