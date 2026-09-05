package com.datn.financeapp.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * M1: gửi mã đặt lại mật khẩu qua SMTP thật (Gmail app password).
 *
 * <p>Chỉ hoạt động ngoài profile {@code test} — xem {@link LogPasswordResetNotifier} cho lý do:
 * test tuyệt đối không được gửi mail ra ngoài.
 *
 * <p>Nội dung mail là plain text có chủ đích: mã 6 chữ số phải đọc và gõ lại được trên điện
 * thoại, HTML không thêm giá trị gì mà lại dễ bị lọc vào thư rác.
 */
@Component
@Profile("!test")
@Slf4j
public class SmtpPasswordResetNotifier implements PasswordResetNotifier {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetNotifier(JavaMailSender mailSender,
                                     @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendResetCode(String email, String rawResetCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(email);
        message.setSubject("Mã đặt lại mật khẩu ứng dụng Quản lý tài chính");
        message.setText("""
                Bạn vừa yêu cầu đặt lại mật khẩu.

                Mã xác nhận của bạn là: %s

                Mã có hiệu lực trong 15 phút và chỉ dùng được một lần.

                Nếu không phải bạn yêu cầu thì bỏ qua email này, mật khẩu hiện tại vẫn giữ nguyên.
                """.formatted(rawResetCode));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Không ném ngược lên AuthService: mã đã lưu vào CSDL rồi, và forgot-password luôn
            // trả 200 dù email có tồn tại hay không (tránh dò danh sách tài khoản). Ném lỗi ở
            // đây sẽ làm lộ đúng thứ đang cố giấu.
            log.error("Không gửi được mã đặt lại mật khẩu tới {}: {}", email, e.getMessage());
        }
    }
}
