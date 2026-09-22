package com.datn.financeapp.group.dto.response.wallet;

import com.datn.financeapp.group.enums.GroupWalletStatus;
import java.time.Instant;
import java.util.UUID;

public record GroupWalletRes(
        UUID id,
        UUID groupId,
        UUID heldByUserId,
        Long currentBalance,
        GroupWalletStatus status,
        Instant createdAt) {
}
