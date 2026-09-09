package com.datn.financeapp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// POST /auth/reset-password (api/01-XAC-THUC.md mục 9).
public record ResetPasswordRequest(
        @NotBlank String resetCode,
        @NotBlank
                @Size(min = 8, message = "Mật khẩu phải từ 8 ký tự.")
                String newPassword) {
}
