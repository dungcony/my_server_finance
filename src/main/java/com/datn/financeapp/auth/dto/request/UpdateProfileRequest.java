package com.datn.financeapp.auth.dto.request;

import jakarta.validation.constraints.Size;

/**
 * PATCH /auth/me (AUTH-05, api/01-XAC-THUC.md mục 6). CHỈ 2 field — không có email, không có
 * plan, để không có cách nào đổi 2 trường đó qua endpoint này.
 *
 * <p>{@code username} không có ràng buộc định dạng và không bắt duy nhất (V12) — chỉ giới hạn
 * độ dài theo cột. Tên tiếng Việt có dấu, chữ hoa, khoảng trắng đều hợp lệ.
 */
public record UpdateProfileRequest(@Size(max = 100) String username, String avatarUrl) {
}
