package com.datn.financeapp.group.dto.group;

import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.MemberStatus;
import java.time.Instant;
import java.util.UUID;

public record GroupMemberRes(
        UUID id,
        UUID userId,
        GroupRole role,
        MemberStatus status,
        Instant joinedAt) {
}
