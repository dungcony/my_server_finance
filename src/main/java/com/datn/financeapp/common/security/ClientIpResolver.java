package com.datn.financeapp.common.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Lấy IP thật của client — ưu tiên header {@code X-Forwarded-For} (khi có proxy/load balancer
 * đứng trước), fallback {@code request.getRemoteAddr()}. Dùng cho AUTH-07 (khoá đăng nhập theo
 * IP) và CORE-04 (rate limit theo IP cho /auth/**).
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
