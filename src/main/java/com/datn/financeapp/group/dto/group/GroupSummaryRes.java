package com.datn.financeapp.group.dto.group;

import com.datn.financeapp.group.enums.GroupRole;
import java.time.Instant;
import java.util.UUID;

public record GroupSummaryRes(
        UUID id,
        String name,
        GroupRole myRole,
        long memberCount,
        long walletCount,
        Long totalFunds,
        Long target,
        Instant createdAt) {
}
