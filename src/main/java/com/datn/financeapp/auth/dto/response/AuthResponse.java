package com.datn.financeapp.auth.dto.response;

import com.datn.financeapp.user.dto.response.UserRes;


// Phản hồi POST /auth/register và POST /auth/login (api/01-XAC-THUC.md mục 1-2).
public record AuthResponse(
        UserRes user,
        String accessToken,
        String refreshToken,
        long expiresIn) {
}
