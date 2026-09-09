package com.datn.financeapp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

// Body của POST /auth/refresh (api/01-XAC-THUC.md mục 3).
public record RefreshRequest(@NotBlank(message = "Thiếu thẻ làm mới.") String refreshToken) {
}
