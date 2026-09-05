package com.datn.financeapp.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** GET /auth/me (api/01-XAC-THUC.md mục 5) — hồ sơ đầy đủ kèm thống kê. */
public record UserDetailDto(
        UUID id,
        String email,
        String username,
        String avatarUrl,
        String plan,
        Instant createdAt,
        Instant lastLoginAt,
        UserStatsDto stats) {
}
