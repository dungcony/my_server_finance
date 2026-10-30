package com.datn.financeapp.module.debt.events;

import com.datn.financeapp.module.debt.enums.DebtType;

import java.util.UUID;

public record DebtCompletedEvent(
        UUID debtId,
        UUID userId,
        String name,
        Long amount,
        DebtType type
) {
}
