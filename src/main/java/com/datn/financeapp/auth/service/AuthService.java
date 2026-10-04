package com.datn.financeapp.auth.service;

import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.RefreshRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.ResendVerificationRequest;
import com.datn.financeapp.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.auth.dto.response.AuthRes;
import com.datn.financeapp.auth.dto.response.RegisterRes;
import com.datn.financeapp.auth.dto.response.TokenRes;

import java.util.UUID;

// Public API của module Auth: Vòng đời phiên đăng nhập và xác thực tài khoản.
public interface AuthService {

    // AUTH-01: Đăng ký tài khoản mới bằng email.
    RegisterRes register(RegisterRequest req);

    // Xác thực email người dùng bằng mã OTP 6 chữ số lưu trong Redis.
    AuthRes verifyEmail(VerifyEmailRequest req);

    // Gửi lại mã OTP xác thực email (áp dụng rate limit cooldown 60 giây).
    void resendVerification(ResendVerificationRequest req);

    //Refresh access token và xoay vòng refresh token (kèm reuse detection).
    TokenRes refresh(RefreshRequest req);

    //Đăng xuất phiên hiện tại hoặc toàn bộ thiết bị.
    void logout(UUID userId, String refreshToken, boolean isAll);

    //  Yêu cầu mã đặt lại mật khẩu qua email.
    void forgotPassword(ForgotPasswordRequest req);

    //  Đặt lại mật khẩu bằng mã 6 chữ số.
    void resetPassword(ResetPasswordRequest req);
}
