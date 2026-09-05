package com.datn.financeapp.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * D-22: implementation DUY NHẤT ở Phase 1, dùng cho cả profile {@code dev} và {@code test} —
 * ghi mã đặt lại ra log thay vì gửi email thật (đồ án chưa có hạ tầng SMTP).
 *
 * <p>T-05-02 (accept): mã reset dạng plaintext ghi ra log chỉ chấp nhận được ở môi trường
 * dev/đồ án tốt nghiệp — TUYỆT ĐỐI không dùng ở production thật.
 *
 * <p>M2: từ khi có {@link SmtpPasswordResetNotifier}, bản này chỉ còn dùng cho profile
 * {@code test}. ĐỪNG XOÁ — bốn file test auth đang {@code @MockitoSpyBean} lên
 * {@link PasswordResetNotifier}, và test tuyệt đối không được gửi mail thật ra ngoài.
 */
@Component
@Profile("test")
@Slf4j
public class LogPasswordResetNotifier implements PasswordResetNotifier {

    @Override
    public void sendResetCode(String email, String rawResetCode) {
        log.info("Mã đặt lại mật khẩu cho {}: {} (hạn 15 phút)", email, rawResetCode);
    }
}
