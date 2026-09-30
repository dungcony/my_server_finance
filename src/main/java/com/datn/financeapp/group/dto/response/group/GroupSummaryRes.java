package com.datn.financeapp.group.dto.response.group;

import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.GroupStatus;

import java.time.Instant;
import java.util.UUID;

public record GroupSummaryRes(
        UUID id,
        String name,
        MemberRole myRole,
        GroupStatus status,
        String inviteCode,
        Long memberCount,
        Long fundBalance,
        Long target,
        Instant createdAt) {

    public GroupSummaryRes {
        if (fundBalance == null) fundBalance = 0L;
    }
}
