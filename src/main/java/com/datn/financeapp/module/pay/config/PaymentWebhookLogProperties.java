package com.datn.financeapp.module.pay.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình nhật ký webhook payOS: số ngày giữ log và ngưỡng chờ webhook trước khi cảnh báo.
 */
@Validated
@ConfigurationProperties(prefix = "payos.webhook-log")
public record PaymentWebhookLogProperties(
        @Positive int retentionDays,
        @Positive int missingMinutes
) {
}
