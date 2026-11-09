package com.datn.financeapp.module.debt.events;

import java.util.UUID;

public record DebtDueReminderEvent(
        UUID debtId,
        UUID userId,
        String name,
        long daysUntilDue,
        String content
) {
}
