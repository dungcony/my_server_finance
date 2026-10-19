package com.datn.financeapp.module.user.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.user.dto.request.DeleteAccountRequest;
import com.datn.financeapp.module.user.dto.request.PremiumUpgradeReq;
import com.datn.financeapp.module.user.dto.request.UpdatePassReq;
import com.datn.financeapp.module.user.dto.request.ProfileUpdateRequest;
import com.datn.financeapp.module.user.service.UserBehavierService;
import com.datn.financeapp.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller quản lý thông tin hồ sơ và tài khoản người dùng.
 * Hỗ trợ đồng thời prefix /users/me và /auth/me để tương thích 100% với client hiện tại.
 */
@RestController
@RequestMapping({"/users",})
@RequiredArgsConstructor
public class UserController {
    private final UserBehavierService userBehavierService;
    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<?> me() {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(userBehavierService.getMe(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<?> updateProfile(@Valid @RequestBody ProfileUpdateRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(userBehavierService.updateMe(userId, req));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody UpdatePassReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        userBehavierService.changePassword(userId, req);
        return ApiResponse.of(null);
    }

    @Idempotent
    @PostMapping("/me/password")
    public ApiResponse<Void> generatePassword() {
        UUID userId = SecurityContextUtil.currentUserId();
        userBehavierService.createPassword(userId);
        return ApiResponse.of(null, "Mật khẩu đã được gửi về email của bạn.");
    }

    @Idempotent
    @PostMapping("/me/premium-upgrades")
    public ApiResponse<PaymentCreateRes> upgradePremium(@Valid @RequestBody PremiumUpgradeReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(userService.createPremiumUpgrade(userId, req.planCode()));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAccount(@Valid @RequestBody(required = false) DeleteAccountRequest req) {
        userBehavierService.deleteMe(req != null ? req.password() : null);
        return ApiResponse.of(null);
    }
}
