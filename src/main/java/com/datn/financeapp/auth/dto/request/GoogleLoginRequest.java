package com.datn.financeapp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Body của POST /auth/google (api/01-XAC-THUC.md mục 13). */
public record GoogleLoginRequest(
        @NotBlank(message = "Thiếu id_token.") String idToken) {
}
