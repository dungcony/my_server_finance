package com.datn.financeapp.module.group.dto.request.member;

import com.datn.financeapp.module.group.enums.MemberRole;
import com.datn.financeapp.module.group.enums.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record MemberUpdateReq(
        UUID id,
        UUID userId,
        MemberRole role,
        MemberStatus status,
        Instant joinedAt) {
}
