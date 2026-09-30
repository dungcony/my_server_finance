package com.datn.financeapp.group.dto.request.member;

import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record MemberUpdateReq(
        UUID id,
        UUID userId,
        MemberRole role,
        MemberStatus status,
        Instant joinedAt) {
}
