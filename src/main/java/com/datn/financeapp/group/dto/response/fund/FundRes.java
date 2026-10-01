package com.datn.financeapp.group.dto.response.fund;

import java.time.Instant;
import java.util.UUID;

public record FundRes(
        UUID id,
        UUID groupId,
        UUID keepperId,
        Long currentBalance,
        Instant createdAt) {
}
