package com.datn.financeapp.module.budget.scheduler;

import com.datn.financeapp.module.budget.service.BudgetRenewalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled Job tự động lặp lại các kỳ ngân sách đã hết hạn có bật tính năng tự động gia hạn.
 * Sử dụng {@link BudgetRenewalService} để gia hạn ngân sách.
 * Tham chiếu phương thức thực thi chính {@link #run()}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetRenewalJob {

    private final BudgetRenewalService budgetRenewalService;

    /**
     * Thực thi tác vụ định kỳ lặp lại các ngân sách hết hạn lúc 02:00 sáng (Asia/Ho_Chi_Minh).
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        try {
            budgetRenewalService.renewExpiredBudgets();
            log.info("Đã chạy xong tác vụ tự động lặp kỳ ngân sách");
        } catch (Exception e) {
            log.error("Lỗi khi chạy tác vụ tự động lặp kỳ ngân sách", e);
        }
    }
}
