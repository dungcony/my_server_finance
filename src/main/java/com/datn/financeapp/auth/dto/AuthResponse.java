package com.datn.financeapp.auth.dto;

/** Phản hồi POST /auth/register và POST /auth/login (api/01-XAC-THUC.md mục 1-2). */
public record AuthResponse(
        UserSummaryResponse user, String accessToken, String refreshToken, long expiresIn) {
}
