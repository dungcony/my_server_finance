package com.datn.financeapp.module.debt.dto.response;

import com.datn.financeapp.module.debt.enums.DebtStatus;
import com.datn.financeapp.module.debt.enums.DebtType;

import java.time.LocalDate;
import java.util.UUID;

public record DebtRes(
        UUID id,
        UUID walletId,
        UUID userId,
        String name,
        DebtType type,
        DebtStatus status,
        Long totalAmount,
        Long paidAmount,
        LocalDate issuedDate,
        LocalDate dueDate,
        String note
) {
}
