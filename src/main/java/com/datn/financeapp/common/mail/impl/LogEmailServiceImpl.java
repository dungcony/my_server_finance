package com.datn.financeapp.common.mail.impl;

import com.datn.financeapp.common.mail.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Giả lập gửi mail bằng cách in log ra console.
 * Dùng cho profile "test" để integration tests không kết nối ra mạng ngoài.
 */
@Service
@Profile("test")
@Slf4j
public class LogEmailServiceImpl implements EmailService {

    @Override
    public void sendPasswordResetCode(String email, String rawResetCode) {
        log.info("Mã đặt lại mật khẩu cho {}: {} (hạn 15 phút)", email, rawResetCode);
    }

    @Override
    public void sendVerificationOtp(String email, String otpCode) {
        log.info("Mã xác thực email cho {}: {} (hạn 15 phút)", email, otpCode);
    }

    @Override
    public void sendEmail(String to, String subject, String content) {
        log.info("Email gửi tới [{}]: Tiêu đề '{}' - Nội dung: {}", to, subject, content);
    }
}
