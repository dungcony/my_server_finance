package com.datn.financeapp.user.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.user.dto.request.ChangePasswordRequest;
import com.datn.financeapp.user.dto.request.DeleteAccountRequest;
import com.datn.financeapp.user.dto.request.UpdateMeRequest;
import com.datn.financeapp.user.dto.response.UserDetailResponse;
import com.datn.financeapp.user.dto.response.UserSummaryResponse;
import com.datn.financeapp.user.service.UserAccountService;
import com.datn.financeapp.user.service.UserProfileService;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý thông tin hồ sơ và tài khoản người dùng.
 * Hỗ trợ đồng thời prefix /users/me và /auth/me để tương thích 100% với client hiện tại.
 */
@RestController
@RequestMapping({"/users", "/auth"})
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final UserAccountService userAccountService;

    @GetMapping("/me")
    public ApiResponse<UserDetailResponse> me() {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(userProfileService.getMe(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<UserSummaryResponse> updateProfile(@Valid @RequestBody UpdateMeRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(userProfileService.updateMe(userId, req));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        userAccountService.changePassword(userId, req);
        return ApiResponse.of(null);
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        userAccountService.deleteAccount(userId, req);
        return ApiResponse.of(null);
    }
}
