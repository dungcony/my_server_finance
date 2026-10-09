package com.datn.financeapp.module.scheduler;

import com.datn.financeapp.module.report.service.ExportCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled Job định kỳ dọn dẹp các tệp báo cáo đã xuất quá hạn lưu trữ.
 * Sử dụng {@link ExportCleanupService} để thực thi việc dọn dẹp.
 * Tham chiếu phương thức thực thi chính {@link #run()}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExportCleanupJob {

    private final ExportCleanupService exportCleanupService;

    /**
     * Thực thi tác vụ dọn dẹp tệp xuất quá hạn lúc 04:30 sáng (Asia/Ho_Chi_Minh).
     */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        exportCleanupService.cleanUpExpiredAndStuck();
    }
}
