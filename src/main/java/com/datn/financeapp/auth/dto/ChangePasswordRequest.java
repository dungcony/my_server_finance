package com.datn.financeapp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/change-password (api/01-XAC-THUC.md mục 7). */
public record ChangePasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank
                @Size(min = 8, message = "Mật khẩu phải từ 8 ký tự.")
                String newPassword) {
}
