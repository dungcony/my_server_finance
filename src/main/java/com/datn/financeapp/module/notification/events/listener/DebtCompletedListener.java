package com.datn.financeapp.module.notification.events.listener;

import com.datn.financeapp.module.debt.enums.DebtType;
import com.datn.financeapp.module.debt.events.DebtCompletedEvent;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Listener nhận sự kiện {@link DebtCompletedEvent} khi một khoản nợ được tất toán thành công
 * và gửi thông báo tới người dùng.
 */
@Slf4j
@Async("notificationTaskExecutor")
@Component
@RequiredArgsConstructor
public class DebtCompletedListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDebtCompleted(DebtCompletedEvent event) {
        try {
            String formattedAmount = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(event.amount());
            String title = "Khoản nợ hoàn thành! 🎉";
            String content = event.type() == DebtType.LENDING
                    ? "Bạn đã thu đủ số tiền " + formattedAmount + "đ từ khoản cho vay \"" + event.name() + "\"."
                    : "Bạn đã hoàn tất thanh toán khoản nợ \"" + event.name() + "\" với số tiền " + formattedAmount + "đ.";

            Map<String, Object> data = Map.of(
                    "action", "VIEW_DEBT",
                    "debt_id", event.debtId().toString()
            );

            notificationService.createNotification(
                    event.userId(),
                    NotiType.DEBT_REMINDER,
                    title,
                    content,
                    event.debtId(),
                    data
            );
            log.info("Gửi thông báo hoàn thành khoản nợ {} cho user {} thành công", event.debtId(), event.userId());
        } catch (RuntimeException ex) {
            log.error("Gửi thông báo hoàn thành khoản nợ {} thất bại", event.debtId(), ex);
        }
    }
}
