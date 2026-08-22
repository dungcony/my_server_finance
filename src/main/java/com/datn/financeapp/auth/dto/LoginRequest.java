package com.datn.financeapp.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body của POST /auth/login (api/01-XAC-THUC.md mục 2). */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
