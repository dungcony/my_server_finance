package com.datn.financeapp.module.pay.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình payOS. {@code returnUrl}/{@code cancelUrl} bắt buộc lấy từ biến môi trường để không bao giờ
 * sinh ra link chuyển hướng sai; thiếu thì app báo lỗi ngay khi khởi động.
 */
@Validated
@ConfigurationProperties(prefix = "payos")
public record PayOsProperties(
        boolean enabled,
        String clientId,
        String apiKey,
        String checksumKey,
        String baseUrl,
        @NotBlank String returnUrl,
        @NotBlank String cancelUrl,
        @Min(1) long expiredMinutes
) {
    public boolean configured() {
        return enabled
                && hasText(clientId)
                && hasText(apiKey)
                && hasText(checksumKey);
    }

    public String resolvedReturnUrl() {
        return returnUrl.trim();
    }

    public String resolvedCancelUrl() {
        return cancelUrl.trim();
    }

    public String resolvedBaseUrl() {
        return hasText(baseUrl) ? baseUrl.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
