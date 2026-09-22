package com.datn.financeapp.group.dto.response.group;

import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.GroupStatus;
import java.time.Instant;
import java.util.UUID;

public record GroupSummaryRes(
        UUID id,
        String name,
        GroupRole myRole,
        GroupStatus status,
        long memberCount,
        Long fundBalance,
        Long target,
        Instant createdAt) {
}
