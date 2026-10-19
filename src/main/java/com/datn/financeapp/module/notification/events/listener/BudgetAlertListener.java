package com.datn.financeapp.module.notification.events.listener;

import com.datn.financeapp.module.budget.events.BudgetAlertEvent;
import com.datn.financeapp.module.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener nhận sự kiện {@link BudgetAlertEvent} khi chi tiêu đạt ngưỡng cảnh báo ngân sách
 * và lưu thông báo cảnh báo vào CSDL (chống trùng lặp theo ngày qua ON CONFLICT DO NOTHING).
 */
@Slf4j
@Async("notificationTaskExecutor")
@Component
@RequiredArgsConstructor
public class BudgetAlertListener {

    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBudgetAlert(BudgetAlertEvent event) {
        try {
            notificationRepository.insertBudgetAlertIfNotExists(
                    event.userId(),
                    event.title(),
                    event.content(),
                    event.budgetId()
            );
            log.info("Lưu cảnh báo ngân sách {} cho user {} thành công", event.budgetId(), event.userId());
        } catch (RuntimeException ex) {
            log.error("Gửi thông báo cảnh báo ngân sách {} thất bại", event.budgetId(), ex);
        }
    }
}
