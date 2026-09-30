package com.datn.financeapp.group.dto.response.transaction;

import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.enums.MoneySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupTransactionDetailRes(
        UUID id,
        UUID groupId,
        UUID transactorId,
        UUID createdBy,
        UUID categoryId,
        MoneySource moneySource,
        TransactionType type,
        TransactionStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        Long amount,
        Instant occurredAt,
        String note,
        Instant createdAt,
        Instant updatedAt,
        List<GroupTransactionParticipantRes> participants) {
}
