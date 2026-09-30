package com.datn.financeapp.group.dto.response.member;

import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record MemberRes(
        UUID id,
        UUID userId,
        MemberRole role,
        MemberStatus status,
        Instant joinedAt) {
}
