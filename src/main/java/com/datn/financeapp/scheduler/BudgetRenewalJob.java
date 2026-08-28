package com.datn.financeapp.scheduler;

import com.datn.financeapp.budget.service.BudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * JOB-02 (D-57, api/05-NGAN-SACH.md mục 8). Tự động lặp kỳ ngân sách {@code auto_renew=true} đã
 * hết kỳ. Chạy 2h sáng giờ Việt Nam. Bọc try/catch để một lần lỗi không làm crash scheduler pool.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetRenewalJob {

    private final BudgetService budgetService;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        try {
            budgetService.renewExpiredBudgets();
            log.info("Đã chạy xong tác vụ tự động lặp kỳ ngân sách");
        } catch (Exception e) {
            log.error("Lỗi khi chạy tác vụ tự động lặp kỳ ngân sách", e);
        }
    }
}
