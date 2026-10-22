package com.datn.financeapp.module.user.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UnlockUserRequest(
        @NotNull(message = "không được để trống người dùng")
        UUID userId
) {
}
