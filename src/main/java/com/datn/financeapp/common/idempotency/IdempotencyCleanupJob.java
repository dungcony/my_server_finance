package com.datn.financeapp.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled Job định kỳ dọn dẹp các idempotency key quá hạn lưu trữ.
 * Sử dụng {@link IdempotencyKeyRepository} để dọn dẹp.
 * Tham chiếu phương thức thực thi chính {@link #cleanup()}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupJob {

    private final IdempotencyKeyRepository repository;

    /**
     * Thực thi tác vụ dọn dẹp idempotency key hết hạn (>24 giờ) lúc 03:00 sáng (Asia/Ho_Chi_Minh).
     */
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
