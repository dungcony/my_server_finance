package com.datn.financeapp.module.pay.config;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình thời hạn nạp bù tiền khi người dùng thanh toán thiếu.
 */
@Validated
@ConfigurationProperties(prefix = "payment.topup")
public record PaymentTopupProperties(
        // thời gian cho phép nạp bù tiền tính bằng giây (mặc định 24 giờ)
        @Positive long seconds,

        // khoảng thời gian trước hạn chót để gửi thông báo nhắc nhở tính bằng giây (mặc định 1 giờ)
        @PositiveOrZero long reminderSeconds,

        // chu kỳ quét định kỳ của job kiểm tra quá hạn bù tiền tính bằng giây (mặc định 60 giây)
        @Positive long jobSeconds
) {
}
