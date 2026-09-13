package com.datn.financeapp.common.mail;

/**
 * Technical service gửi email dùng chung cho toàn bộ hệ thống (common/mail).
 * Thay thế cho PasswordResetNotifier nằm lẻ loi trong auth.
 */
public interface EmailService {

    /**
     * Gửi mã xác nhận đặt lại mật khẩu (6 chữ số).
     *
     * @param email địa chỉ email người nhận
     * @param rawResetCode mã 6 số
     */
    void sendPasswordResetCode(String email, String rawResetCode);

    /**
     * Gửi mã OTP 6 số xác thực tài khoản sau khi đăng ký.
     *
     * @param email địa chỉ email người nhận
     * @param otpCode mã OTP 6 số
     */
    void sendVerificationOtp(String email, String otpCode);

    /**
     * Gửi email chung (dự phòng cho xác thực email, thông báo...).
     *
     * @param to địa chỉ email người nhận
     * @param subject tiêu đề email
     * @param content nội dung email
     */
    void sendEmail(String to, String subject, String content);
}
