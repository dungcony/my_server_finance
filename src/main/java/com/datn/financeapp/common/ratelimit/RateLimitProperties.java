package com.datn.financeapp.common.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Bảng cấu hình rate limit 3 tầng đã chốt ở D-16 — hardcode (không đọc từ application.yml)
 * để giữ đơn giản, nhưng đặt trong bean để dễ mock/test.
 *
 * | Nhóm    | Quota      | Đếm theo |
 * |---------|------------|----------|
 * | auth    | 5/phút     | IP       |
 * | ai      | 30/phút    | user_id  |
 * | default | 120/phút   | user_id  |
 */
@Component
public class RateLimitProperties {

    public static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("auth", "/auth/", 5, Duration.ofMinutes(1), true),
            new RateLimitRule("ai", "/ai/", 30, Duration.ofMinutes(1), false),
            new RateLimitRule("default", "/", 120, Duration.ofMinutes(1), false));

    public RateLimitRule resolve(String requestUri) {
        return RULES.stream()
                .filter(r -> !r.pathPrefix().equals("/") && requestUri.startsWith(r.pathPrefix()))
                .findFirst()
                .orElseGet(() -> RULES.stream()
                        .filter(r -> r.pathPrefix().equals("/"))
                        .findFirst()
                        .orElseThrow());
    }
}
