package com.datn.financeapp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /auth/change-password (api/01-XAC-THUC.md mục 7). */
public record ChangePasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank
                @Size(min = 8)
                @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu phải có ít nhất một chữ và một số.")
                String newPassword) {
}
