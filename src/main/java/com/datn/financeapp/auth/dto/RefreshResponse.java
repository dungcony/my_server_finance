package com.datn.financeapp.auth.dto;

/**
 * Phản hồi POST /auth/refresh (api/01-XAC-THUC.md mục 3) — KHÔNG có trường "user", khác với
 * {@link AuthResponse} dùng cho register/login.
 */
public record RefreshResponse(String accessToken, String refreshToken, long expiresIn) {
}
