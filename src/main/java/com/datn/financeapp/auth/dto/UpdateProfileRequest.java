package com.datn.financeapp.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH /auth/me (AUTH-05, api/01-XAC-THUC.md mục 6). CHỈ 2 field — không có email, không có
 * plan, để không có cách nào đổi 2 trường đó qua endpoint này.
 */
public record UpdateProfileRequest(@Size(min = 2, max = 100) String fullName, String avatarUrl) {
}
