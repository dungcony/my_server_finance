package com.datn.financeapp.user.service;

import com.datn.financeapp.user.dto.request.UpdateProfileRequest;
import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.user.dto.response.UserProfileResponse;
import com.datn.financeapp.user.dto.response.UserRes;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface UserBehavierService {

    // Lấy thông tin chi tiết cá nhân kèm thống kê ví, giao dịch (GET /users/me hoặc /auth/me).
    UserRes getMe(UUID userId);

    // Cập nhật thông tin hồ sơ: tên hiển thị, avatar (PATCH /users/me hoặc /auth/me).
    UserRes updateMe(UUID userId, UpdateProfileRequest req);

    // Xóa mềm tài khoản.
    void deleteMe(String password);

    void changePassword(UUID userId, UpdatePassReq req);

    void createPassword(UUID userId);
}
