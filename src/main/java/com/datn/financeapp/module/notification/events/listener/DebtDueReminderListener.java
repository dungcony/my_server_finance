package com.datn.financeapp.module.notification.events.listener;

import com.datn.financeapp.module.debt.events.DebtDueReminderEvent;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listener nhận sự kiện {@link DebtDueReminderEvent} khi khoản nợ sắp đến hạn hoặc quá hạn
 * và ghi nhận thông báo nhắc nhở cho người dùng.
 */
@Slf4j
@Async("notificationTaskExecutor")
@Component
@RequiredArgsConstructor
public class DebtDueReminderListener {

    private final NotificationService notificationService;

    @EventListener
    public void onDebtDueReminder(DebtDueReminderEvent event) {
        try {
            Map<String, Object> data = Map.of(
                    "action", "VIEW_DEBT",
                    "debt_id", event.debtId().toString()
            );

            notificationService.createNotification(
                    event.userId(),
                    NotiType.DEBT_REMINDER,
                    "Nhắc nợ đến hạn",
                    event.content(),
                    event.debtId(),
                    data
            );
            log.info("Gửi thông báo nhắc nợ {} cho user {} thành công", event.debtId(), event.userId());
        } catch (RuntimeException ex) {
            log.error("Gửi thông báo nhắc nợ {} thất bại", event.debtId(), ex);
        }
    }
}
