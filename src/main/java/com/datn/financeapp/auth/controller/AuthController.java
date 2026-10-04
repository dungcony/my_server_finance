package com.datn.financeapp.auth.controller;

import com.datn.financeapp.auth.dto.request.*;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller chuyên trách xác thực và vòng đời phiên đăng nhập.
 * Các endpoint hồ sơ cá nhân (/me), đổi mật khẩu (/me/password) và xóa tài khoản (/me)
 * được chuyển sang UserController.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.of(authService.register(req));
    }

    @PostMapping("/verify-email")
    public ApiResponse<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        return ApiResponse.of(authService.verifyEmail(req));
    }

    @PostMapping("/resend-verification")
    public ApiResponse<?> resendVerification(@Valid @RequestBody ResendVerificationRequest req) {
        authService.resendVerification(req);
        return ApiResponse.of(Map.of("message", "Mã xác thực mới đã được gửi tới email của bạn."));
    }


    @PostMapping("/refresh")
    public ApiResponse<?> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.of(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(@RequestBody LogoutRequest req) {
        authService.logout(SecurityContextUtil.currentUserId(), req.refreshToken(), req.logoutAllDevices());
        return ApiResponse.of(null);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ApiResponse.of(null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ApiResponse.of(null);
    }
}
