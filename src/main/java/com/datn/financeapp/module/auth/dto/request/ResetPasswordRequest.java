package com.datn.financeapp.module.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// POST /auth/reset-password (api/01-XAC-THUC.md mục 9).
public record ResetPasswordRequest(
        @NotBlank(message = "Email không được để trống.") @Email(message = "Email không đúng định dạng.") String email,
        @NotBlank(message = "Mã đặt lại mật khẩu không được để trống.") String resetCode,
        @NotBlank(message = "Mật khẩu mới không được để trống.")
        @Size(min = 8, message = "Mật khẩu phải từ 8 ký tự.")
        String newPassword) {
}
