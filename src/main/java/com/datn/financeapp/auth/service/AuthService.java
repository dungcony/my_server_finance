package com.datn.financeapp.auth.service;

import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.GoogleLoginRequest;
import com.datn.financeapp.auth.dto.request.LoginRequest;
import com.datn.financeapp.auth.dto.request.RefreshRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.ResendVerificationRequest;
import com.datn.financeapp.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.auth.dto.response.AuthResponse;
import com.datn.financeapp.auth.dto.response.RefreshResponse;
import com.datn.financeapp.auth.dto.response.RegisterResponse;

import java.util.UUID;

// Public API của module Auth: Vòng đời phiên đăng nhập và xác thực tài khoản.
public interface AuthService {

    // AUTH-01: Đăng ký tài khoản mới bằng email.
    RegisterResponse register(RegisterRequest req);

    // Xác thực email người dùng bằng mã OTP 6 chữ số lưu trong Redis.
    AuthResponse verifyEmail(VerifyEmailRequest req);

    // Gửi lại mã OTP xác thực email (áp dụng rate limit cooldown 60 giây).
    void resendVerification(ResendVerificationRequest req);

    // AUTH-02: Đăng nhập bằng email và mật khẩu (kèm chống brute-force lockout 5 lần).
    AuthResponse login(LoginRequest req, String ipAddress, String userAgent);

    // D3: Đăng nhập bằng Google ID Token.
    AuthResponse loginWithGoogle(GoogleLoginRequest req);

    // AUTH-03: Refresh access token và xoay vòng refresh token (kèm reuse detection).
    RefreshResponse refresh(RefreshRequest req);

    // AUTH-04: Đăng xuất phiên hiện tại hoặc toàn bộ thiết bị.
    void logout(UUID userId, String rawRefreshToken, boolean logoutAllDevices);

    // AUTH-06: Yêu cầu mã đặt lại mật khẩu qua email.
    void forgotPassword(ForgotPasswordRequest req);

    // AUTH-06: Đặt lại mật khẩu bằng mã 6 chữ số.
    void resetPassword(ResetPasswordRequest req);

    // Thu hồi toàn bộ refresh token còn hiệu lực của một người dùng.
    void revokeAllTokensForUser(UUID userId);
}
