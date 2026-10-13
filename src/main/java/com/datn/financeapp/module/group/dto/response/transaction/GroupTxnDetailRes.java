package com.datn.financeapp.module.group.dto.response.transaction;

import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;

import java.time.Instant;
import java.util.UUID;

public record GroupTxnDetailRes(
        UUID id,
        UUID groupId,
        UUID transactorId,
        UUID createdBy,
        String categoryCode,
        MoneySource moneySource,
        GroupTxnType type,
        GroupTxnStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        Long amount,
        Instant occurredAt,
        String note,
        Instant createdAt,
        Instant updatedAt) {
}
