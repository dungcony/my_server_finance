package com.datn.financeapp.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** GET /auth/me (api/01-XAC-THUC.md mục 5) — hồ sơ đầy đủ kèm thống kê. */
public record UserDetailResponse(
        UUID id,
        String email,
        String username,
        String avatarUrl,
        String plan,
        /** Xem ghi chú cùng tên ở {@link UserSummaryResponse} về khoá JSON. */
        @JsonProperty("is_confirm") boolean isConfirm,
        String role,
        /** Xem ghi chú ở {@link UserSummaryResponse}. */
        @JsonProperty("has_password") boolean hasPassword,
        /** Xem ghi chú ở {@link UserSummaryResponse}. */
        @JsonProperty("google_linked") boolean googleLinked,
        Instant createdAt,
        Instant lastLoginAt,
        UserStatsResponse stats) {
}
