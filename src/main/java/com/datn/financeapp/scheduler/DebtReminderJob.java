package com.datn.financeapp.scheduler;

import com.datn.financeapp.debt.service.DebtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * JOB-04 (D-57, api/08-SO-NO.md mục 9). Nhắc nợ đến hạn: còn 7 ngày, còn 1 ngày, quá hạn nhắc lại
 * mỗi 7 ngày. Chạy 4h sáng giờ Việt Nam. Bọc try/catch để một lần lỗi không làm crash scheduler
 * pool.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DebtReminderJob {

    private final DebtService debtService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        try {
            debtService.sendDueReminders();
            log.info("Đã chạy xong tác vụ nhắc nợ đến hạn");
        } catch (Exception e) {
            log.error("Lỗi khi chạy tác vụ nhắc nợ đến hạn", e);
        }
    }
}
