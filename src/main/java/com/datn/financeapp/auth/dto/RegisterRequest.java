package com.datn.financeapp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của POST /auth/register (api/01-XAC-THUC.md mục 1).
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank
        @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Mật khẩu phải có ít nhất một chữ và một số.")
        String password,
        @NotBlank @Size(max = 100) String username) {
}
