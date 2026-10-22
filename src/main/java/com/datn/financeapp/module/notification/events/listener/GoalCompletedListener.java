package com.datn.financeapp.module.notification.events.listener;

import com.datn.financeapp.module.goal.event.GoalCompletedEvent;
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
 * Listener nhận sự kiện {@link GoalCompletedEvent} khi mục tiêu tiết kiệm hoàn thành
 * và gửi thông báo chúc mừng tới người dùng.
 */
@Slf4j
@Async("notificationTaskExecutor")
@Component
@RequiredArgsConstructor
public class GoalCompletedListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGoalCompleted(GoalCompletedEvent event) {
        try {
            String formattedAmount = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(event.targetAmount());
            Map<String, Object> data = Map.of(
                    "action", "VIEW_GOAL",
                    "goal_id", event.goalId().toString()
            );

            notificationService.createNotification(
                    event.userId(),
                    NotiType.GOAL_COMPLETED,
                    "Mục tiêu hoàn thành! 🎉",
                    "Chúc mừng bạn đã hoàn thành mục tiêu \"" + event.goalName() + "\" với số tiền " + formattedAmount + "vnđ!",
                    event.goalId(),
                    data
            );
            log.info("Gửi thông báo hoàn thành mục tiêu {} cho user {} thành công", event.goalId(), event.userId());
        } catch (RuntimeException ex) {
            log.error("Gửi thông báo hoàn thành mục tiêu {} thất bại", event.goalId(), ex);
        }
    }
}
