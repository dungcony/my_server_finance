package com.datn.financeapp.user.dto.request;

import jakarta.validation.constraints.Size;

// PATCH /auth/me hoặc PATCH /users/me.
public record UpdateMeRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        String avatarUrl) {
}
