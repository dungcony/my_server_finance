package com.datn.financeapp.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** Phần "user" trong phản hồi register/login (api/01-XAC-THUC.md mục 1). */
public record UserSummaryDto(
        UUID id, String email, String username, String avatarUrl, String plan, Instant createdAt) {
}
