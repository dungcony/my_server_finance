package com.datn.financeapp.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BlockUserRequest(
        @NotNull(message = "không được để trống người dùng")
        UUID userId,
        @Size(max = 500, message = "Lý do khóa không vượt quá 500 ký tự")
        String reason
) {
}
