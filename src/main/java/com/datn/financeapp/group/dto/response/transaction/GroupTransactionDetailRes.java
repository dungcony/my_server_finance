package com.datn.financeapp.group.dto.response.transaction;

import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MoneySource;

import java.time.Instant;
import java.util.UUID;

public record GroupTransactionDetailRes(
        UUID id,
        UUID groupId,
        UUID transactorId,
        UUID createdBy,
        UUID categoryId,
        MoneySource moneySource,
        GTransactionType type,
        GTransactionStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        Long amount,
        Instant occurredAt,
        String note,
        Instant createdAt,
        Instant updatedAt) {
}
