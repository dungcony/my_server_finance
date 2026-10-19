package com.datn.financeapp.unitTest.notification.events.listener;

import com.datn.financeapp.module.notification.events.listener.*;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.datn.financeapp.module.goal.event.GoalCompletedEvent;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class GoalCompletedListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private GoalCompletedListener goalCompletedListener;

    @Test
    @DisplayName("onGoalCompleted: Gửi thông báo chúc mừng hoàn thành mục tiêu cho user với type GOAL_COMPLETED")
    void onGoalCompleted_notificationSentSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        GoalCompletedEvent event = new GoalCompletedEvent(
                userId,
                goalId,
                "Mua laptop mới",
                25_000_000L
        );

        goalCompletedListener.onGoalCompleted(event);

        verify(notificationService).createNotification(
                eq(userId),
                eq(NotiType.GOAL_COMPLETED),
                eq("Mục tiêu hoàn thành! 🎉"),
                org.mockito.ArgumentMatchers.contains("Mua laptop mới"),
                eq(goalId),
                any()
        );
    }
}
