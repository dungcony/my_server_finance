package com.datn.financeapp.module.pay.scheduler;

import com.datn.financeapp.module.pay.config.PaymentWebhookLogProperties;
import com.datn.financeapp.module.pay.repository.PaymentWebhookLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Dọn nhật ký webhook cũ hơn {@code payos.webhook-log.retention-days} ngày (mặc định 30).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookLogCleanupJob {

    private final PaymentWebhookLogRepository repository;
    private final PaymentWebhookLogProperties properties;
    private final Clock clock;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanup() {
        try {
            int deleted = repository.deleteOlderThan(clock.instant().minus(properties.retentionDays(), ChronoUnit.DAYS));
            log.info("Đã dọn {} bản ghi payment_webhook_logs quá {} ngày", deleted, properties.retentionDays());
        } catch (Exception e) {
            log.error("Lỗi khi dọn payment_webhook_logs", e);
        }
    }
}
