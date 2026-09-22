package com.datn.financeapp.group.dto.response.transaction;

import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GroupTransactionDetailRes(
        UUID id,
        UUID groupId,
        MoneySource moneySource,
        UUID userId,
        UUID createdBy,
        UUID categoryId,
        GroupTransactionType type,
        GroupTransactionStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        Long amount,
        Instant occurredAt,
        LocalDate date,
        UUID groupWalletId,
        UUID personalTransactionId,
        String note,
        Instant createdAt,
        Instant updatedAt,
        Boolean isAllMembers,
        List<GroupTransactionParticipantRes> participants) {
}
