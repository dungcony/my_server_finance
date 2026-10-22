package com.datn.financeapp.module.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// POST /auth/forgot-password (api/01-XAC-THUC.md mục 8).
public record ForgotPasswordRequest(@NotBlank(message = "Email không được để trống.") @Email(message = "Email không đúng định dạng.") String email) {
}
