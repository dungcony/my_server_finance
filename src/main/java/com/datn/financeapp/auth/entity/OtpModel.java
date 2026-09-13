package com.datn.financeapp.auth.entity;

import com.datn.financeapp.auth.enums.OtpType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@RedisHash("otp")
public class OtpModel {
    @Id
    private String id;

    private String email;
    private OtpType type;
    private String code;

    @TimeToLive(unit = TimeUnit.MINUTES)
    private Long ttl;

    private Instant createdAt;

    @Builder
    public OtpModel(OtpType type, String email, String code, Long ttl, Instant createdAt) {
        this.id = buildId(type, email);
        this.type = type;
        this.email = email;
        this.code = code;
        this.ttl = ttl;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static String buildId(OtpType type, String email) {
        return type.name().toLowerCase() + ":" + email.toLowerCase().trim();
    }
}

