package com.datn.financeapp.module.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled Job quét và sinh giao dịch cho các khoản lặp định kỳ đến hạn.
 * Sử dụng {@link RecurringRunnerService} để xử lý nghiệp vụ lặp giao dịch.
 * Tham chiếu phương thức thực thi chính {@link #run()}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionJob {

    private final RecurringRunnerService recurringRunnerService;

    /**
     * Thực thi tác vụ sinh giao dịch định kỳ lúc 03:30 sáng (Asia/Ho_Chi_Minh).
     */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        try {
            recurringRunnerService.runDueRecurring();
            log.info("Đã chạy xong tác vụ sinh giao dịch định kỳ");
        } catch (Exception e) {
            log.error("Lỗi khi chạy tác vụ sinh giao dịch định kỳ", e);
        }
    }
}
