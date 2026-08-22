package com.datn.financeapp.common.ratelimit;

import java.time.Duration;

/**
 * Một quy tắc rate limit (nhóm endpoint -> quota, chiều đếm) — CORE-04, D-16.
 *
 * @param group nhãn nhóm, dùng làm phần tiền tố của bucket key trong Caffeine cache
 * @param pathPrefix tiền tố path để khớp request, ví dụ {@code /auth/}
 * @param limit số request tối đa trong {@code window}
 * @param window khoảng thời gian quota reset
 * @param keyedByIp true = đếm theo IP (dùng cho endpoint chưa xác thực như /auth/**),
 *                  false = đếm theo user_id (D-18: filter đặt sau JwtAuthFilter nên đã có user_id)
 */
public record RateLimitRule(String group, String pathPrefix, int limit, Duration window, boolean keyedByIp) {}
