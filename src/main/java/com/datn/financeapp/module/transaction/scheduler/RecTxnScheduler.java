package com.datn.financeapp.module.transaction.scheduler;

import com.datn.financeapp.module.transaction.service.RecTxnScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Điều phối tác vụ chạy ngầm định kỳ quét và thực thi các buổi lịch phát sinh đến hạn.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecTxnScheduler {

    private final RecTxnScheduleService scheduleService;

    /**
     * Quét các buổi lịch đến hạn và tự động sinh giao dịch thực tế.
     * Mặc định chạy mỗi giờ 1 lần tại phút 00 hoặc theo cấu hình cron {@code app.recurring.cron}.
     */
    @Scheduled(cron = "${app.recurring.cron:0 0 * * * *}")
    public void executeDueSchedules() {
        log.info("Bắt đầu quét và xử lý các buổi lịch giao dịch định kỳ đến hạn");
        scheduleService.processDueSchedules();
        log.info("Hoàn tất quét và xử lý các buổi lịch giao dịch định kỳ");
    }
}
