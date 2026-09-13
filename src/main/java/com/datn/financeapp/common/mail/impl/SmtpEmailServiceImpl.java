package com.datn.financeapp.common.mail.impl;

import com.datn.financeapp.common.mail.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gửi email qua SMTP thật (Gmail app password / Mail server cấu hình trong application.yml).
 * Chỉ kích hoạt khi KHÔNG phải profile "test".
 */
@Async
@Service
@Profile("!test")
@Slf4j
public class SmtpEmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailServiceImpl(JavaMailSender mailSender,
                                @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendPasswordResetCode(String email, String rawResetCode) {
        String subject = "Mã đặt lại mật khẩu ứng dụng Quản lý tài chính";
        String content = """
                Bạn vừa yêu cầu đặt lại mật khẩu.
                
                Mã xác nhận của bạn là: %s
                
                Mã có hiệu lực trong 15 phút và chỉ dùng được một lần.
                
                Nếu không phải bạn yêu cầu thì bỏ qua email này, mật khẩu hiện tại vẫn giữ nguyên.
                """.formatted(rawResetCode);
        sendEmail(email, subject, content);
    }

    @Override
    public void sendVerificationOtp(String email, String otpCode) {
        String subject = "Mã xác thực tài khoản ứng dụng Quản lý tài chính";
        String content = """
                Chào bạn,
                
                Mã xác thực kích hoạt tài khoản của bạn là: %s
                
                Mã có hiệu lực trong 15 phút.
                Vui lòng không chia sẻ mã này cho bất kỳ ai.
                """.formatted(otpCode);
        sendEmail(email, subject, content);
    }
    
    @Override
    public void sendEmail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Không gửi được email tới {}: {}", to, e.getMessage());
        }
    }
}
