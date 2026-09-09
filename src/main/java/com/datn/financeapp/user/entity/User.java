package com.datn.financeapp.user.entity;

import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
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

    public String getRole() {
        return "USER";
    }

    public String getUsername() {
        if (firstName == null && lastName == null) {
            return email != null ? email.substring(0, email.indexOf('@')) : null;
        }
        if (lastName == null) return firstName;
        if (firstName == null) return lastName;
        return (lastName + " " + firstName).trim();
    }

    public void setUsername(String username) {
        if (username == null || username.isBlank()) {
            this.firstName = null;
            this.lastName = null;
            return;
        }
        String trimmed = username.trim();
        int idx = trimmed.lastIndexOf(' ');
        if (idx > 0) {
            this.lastName = trimmed.substring(0, idx).trim();
            this.firstName = trimmed.substring(idx + 1).trim();
        } else {
            this.firstName = trimmed;
            this.lastName = null;
        }
    }

    public String getPasswordHash() {
        return password;
    }

    public void setPasswordHash(String passwordHash) {
        this.password = passwordHash;
    }

    public static class UserBuilder {
        public UserBuilder passwordHash(String passwordHash) {
            this.password = passwordHash;
            return this;
        }

        public UserBuilder username(String username) {
            if (username == null || username.isBlank()) {
                this.firstName = null;
                this.lastName = null;
                return this;
            }
            String trimmed = username.trim();
            int idx = trimmed.lastIndexOf(' ');
            if (idx > 0) {
                this.lastName = trimmed.substring(0, idx).trim();
                this.firstName = trimmed.substring(idx + 1).trim();
            } else {
                this.firstName = trimmed;
                this.lastName = null;
            }
            return this;
        }
    }

    public boolean isConfirm() {
        return status == UserStatus.ACTIVE;
    }

    public void setConfirm(boolean confirm) {
        if (confirm) {
            this.status = UserStatus.ACTIVE;
        } else if (this.status == UserStatus.ACTIVE) {
            this.status = UserStatus.PENDING_VERIFY;
        }
    }

    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }

    public void setBlocked(boolean blocked) {
        if (blocked) {
            this.status = UserStatus.BLOCKED;
        } else if (this.status == UserStatus.BLOCKED) {
            this.status = UserStatus.ACTIVE;
        }
    }
}
