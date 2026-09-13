package com.datn.financeapp.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// POST /auth/forgot-password (api/01-XAC-THUC.md mục 8).
public record ForgotPasswordRequest(@NotBlank @Email String email) {
}
