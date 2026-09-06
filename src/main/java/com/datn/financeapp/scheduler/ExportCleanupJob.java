package com.datn.financeapp.scheduler;

import com.datn.financeapp.report.service.ExportCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hẹn giờ dọn tệp/bản ghi {@code export_jobs} quá hạn (D-45) — 4h30 sáng giờ Việt Nam.
 *
 * <p>Class này chỉ quyết định <b>khi nào</b> chạy. Toàn bộ nghiệp vụ dọn dẹp nằm ở
 * {@link ExportCleanupService} của module {@code report/} — nơi biết {@code export_jobs} nghĩa là
 * gì. Đây là hình mẫu cho mọi job trong package này: {@code scheduler/} là vỏ mỏng, không chứa
 * nghiệp vụ và không đụng {@code Repository}/{@code Entity} của module khác (quy tắc 11
 * CLAUDE.md).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExportCleanupJob {

    private final ExportCleanupService exportCleanupService;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        exportCleanupService.cleanUpExpiredAndStuck();
    }
}
