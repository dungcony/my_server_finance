package com.datn.financeapp.scheduler;

import com.datn.financeapp.recurring.service.RecurringRunnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * JOB-03 (D-57, api/09-DINH-KY-MUC-TIEU.md mục A4). Sinh giao dịch cho khoản định kỳ tới hạn, bắt
 * kịp mọi kỳ bỏ lỡ. Chạy 3h30 sáng giờ Việt Nam. Bọc try/catch để một lần lỗi không làm crash
 * scheduler pool — logic từng bản ghi đã tự bọc try/catch riêng trong
 * {@link RecurringRunnerService#runDueRecurring()} (D-51/D-52), lớp này chỉ thêm một tầng phòng
 * vệ nữa cho lỗi hệ thống nằm ngoài vòng lặp (ví dụ lỗi kết nối CSDL).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionJob {

    private final RecurringRunnerService recurringRunnerService;

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
