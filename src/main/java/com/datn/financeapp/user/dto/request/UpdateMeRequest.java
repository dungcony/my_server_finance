package com.datn.financeapp.user.dto.request;

import jakarta.validation.constraints.Size;

// PATCH /auth/me hoặc PATCH /users/me. CHỈ 2 field — không có email, không có plan.
public record UpdateMeRequest(@Size(max = 100) String username, String avatarUrl) {
}
