package com.datn.financeapp.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Đánh dấu 1 user bị khoá NGAY LẬP TỨC, bất kể access token cũ còn hạn hay không — xem
 * JwtAuthFilter (nơi đọc lại record này trước khi tin authorities trong token). TTL nên
 * đặt bằng jwt.access-token-expiry-seconds: hết TTL này thì token cũ cũng đã tự hết hạn
 * theo claim exp, không cần blacklist giữ thêm.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@RedisHash("blacklisted_user")
public class BlacklistedUserModel {

    @Id
    private String id;

    private String reason;

    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long ttl;

    private Instant blockedAt;

    @Builder
    public BlacklistedUserModel(UUID userId, String reason, Long ttl, Instant blockedAt) {
        this.id = userId.toString();
        this.reason = reason;
        this.ttl = ttl;
        this.blockedAt = blockedAt != null ? blockedAt : Instant.now();
    }
}
