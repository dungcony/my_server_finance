package com.datn.financeapp.module.user.dto.request;

import jakarta.validation.constraints.Size;

// PATCH /auth/me hoặc PATCH /users/me.
public record UpdateProfileRequest(
        @Size(max = 100, message = "Tên không được vượt quá 100 ký tự.") String firstName,
        @Size(max = 100, message = "Họ không được vượt quá 100 ký tự.") String lastName,
        String avatarUrl) {
}
