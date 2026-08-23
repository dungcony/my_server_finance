package com.datn.financeapp.auth.service;

/**
 * D-22: tách interface khỏi implementation gửi mã đặt lại mật khẩu — cho phép thay bằng SMTP
 * thật sau này (Gmail app password / Mailtrap) mà không đụng business logic của
 * {@code AuthService.forgotPassword}.
 */
public interface PasswordResetNotifier {

    void sendResetCode(String email, String rawResetCode);
}
