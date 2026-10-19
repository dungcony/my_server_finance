package com.datn.financeapp.unitTest.notification.events.listener;

import com.datn.financeapp.module.notification.events.listener.*;


import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.datn.financeapp.module.budget.events.BudgetAlertEvent;
import com.datn.financeapp.module.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class BudgetAlertListenerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private BudgetAlertListener budgetAlertListener;

    @Test
    @DisplayName("onBudgetAlert: Ghi thông báo cảnh báo ngân sách vào DB qua insertBudgetAlertIfNotExists")
    void onBudgetAlert_insertsAlertSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        BudgetAlertEvent event = new BudgetAlertEvent(
                userId,
                "Sắp hết ngân sách",
                "Bạn đã tiêu 80% ngân sách",
                budgetId
        );

        budgetAlertListener.onBudgetAlert(event);

        verify(notificationRepository).insertBudgetAlertIfNotExists(
                eq(userId),
                eq("Sắp hết ngân sách"),
                eq("Bạn đã tiêu 80% ngân sách"),
                eq(budgetId)
        );
    }
}
