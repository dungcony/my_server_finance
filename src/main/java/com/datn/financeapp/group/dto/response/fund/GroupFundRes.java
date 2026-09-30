package com.datn.financeapp.group.dto.response.fund;

import java.time.Instant;
import java.util.UUID;

public record GroupFundRes(
        UUID id,
        UUID groupId,
        UUID heldByUserId,
        Long currentBalance,
        Instant createdAt) {
}
