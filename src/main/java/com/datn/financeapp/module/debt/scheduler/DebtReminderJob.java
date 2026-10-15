package com.datn.financeapp.module.debt.scheduler;

import com.datn.financeapp.module.debt.service.DebtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled Job gửi thông báo nhắc nhở nợ đến hạn và quá hạn cho người dùng.
 * Sử dụng {@link DebtService} để kiểm tra và gửi thông báo.
 * Tham chiếu phương thức thực thi chính {@link #run()}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DebtReminderJob {

    private final DebtService debtService;

    /**
     * Thực thi tác vụ gửi thông báo nhắc nợ định kỳ lúc 04:00 sáng (Asia/Ho_Chi_Minh).
     */
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
