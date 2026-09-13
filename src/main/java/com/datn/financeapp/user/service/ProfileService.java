package com.datn.financeapp.user.service;

import com.datn.financeapp.user.dto.request.UpdateMeRequest;
import com.datn.financeapp.user.dto.response.UserProfileResponse;

import java.util.UUID;

/**
 * Service quản lý thông tin hồ sơ người dùng (User Profile).
 */
public interface ProfileService {

    // Lấy thông tin chi tiết cá nhân kèm thống kê ví, giao dịch (GET /users/me hoặc /auth/me).
    UserProfileResponse getMe(UUID userId);

    // Cập nhật thông tin hồ sơ: tên hiển thị, avatar (PATCH /users/me hoặc /auth/me).
    UserProfileResponse updateMe(UUID userId, UpdateMeRequest req);

    // Xóa mềm tài khoản.
    void deleteMe(String password);

}
