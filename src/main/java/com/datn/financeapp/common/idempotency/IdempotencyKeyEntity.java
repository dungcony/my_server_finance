package com.datn.financeapp.common.idempotency;

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
 * Entity khớp bảng {@code idempotency_keys} (V7__ha_tang_xac_thuc.sql).
 * UNIQUE composite {@code uq_idem_scope} = (idempotency_key, user_id, endpoint).
 * {@code response_body} là JSONB — lưu dạng chuỗi JSON, serialize/deserialize
 * bằng Jackson ở tầng Service (Plan 03).
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKeyEntity {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    /** CHECK IN ('processing','completed'). */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
