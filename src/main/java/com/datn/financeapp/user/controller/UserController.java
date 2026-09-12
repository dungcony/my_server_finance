package com.datn.financeapp.user.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.user.dto.request.DeleteAccountRequest;
import com.datn.financeapp.user.dto.request.UpdateMeRequest;
import com.datn.financeapp.user.dto.response.UserProfileResponse;
import com.datn.financeapp.user.service.AccountService;
import com.datn.financeapp.user.service.ProfileService;
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

    private final ProfileService userProfileService;
    private final AccountService userAccountService;

    @GetMapping("/me")
    public ApiResponse<?> me() {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(userProfileService.getMe(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateMeRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(userProfileService.updateMe(userId, req));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody UpdatePassReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        userAccountService.changePassword(userId, req);
        return ApiResponse.of(null);
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAccount(@Valid @RequestBody(required = false) DeleteAccountRequest req) {
        userProfileService.deleteMe(req != null ? req.password() : null);
        return ApiResponse.of(null);
    }
}
