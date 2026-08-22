package com.datn.financeapp.scheduler;

import com.datn.financeapp.common.idempotency.IdempotencyKeyRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dọn bản ghi {@code idempotency_keys} quá 24 giờ (D-15). Chạy 3h sáng giờ Việt Nam hằng ngày.
 * Bọc try/catch để một lần lỗi không làm crash scheduler pool.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupJob {

    private final IdempotencyKeyRepository repository;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanup() {
        try {
            int deleted = repository.deleteOlderThan(Instant.now().minus(24, ChronoUnit.HOURS));
            log.info("Đã dọn {} bản ghi idempotency_keys quá 24 giờ", deleted);
        } catch (Exception e) {
            log.error("Lỗi khi dọn idempotency_keys", e);
        }
    }
}
