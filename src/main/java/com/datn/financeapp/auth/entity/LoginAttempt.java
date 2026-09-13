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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entity khớp bảng {@code login_attempts} (V7__ha_tang_xac_thuc.sql).
 * KHÔNG FK tới {@code users} — ghi cả email không tồn tại (AUTH-07).
 */
@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAttempt {

    @Id
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    // Cột Postgres kiểu INET — map bằng SqlTypes.INET (Hibernate 6.2+).
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "succeeded", nullable = false)
    private Boolean succeeded;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;
}
