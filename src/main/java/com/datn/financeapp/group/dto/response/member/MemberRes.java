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
        Instant joinedAt,
        String displayName,
        Boolean isTreasurer) {

    // dùng khi mới map từ entity, chưa gắn tên hiển thị và cờ thủ quỹ
    public MemberRes(UUID id, UUID userId, MemberRole role, MemberStatus status, Instant joinedAt) {
        this(id, userId, role, status, joinedAt, null, false);
    }

    public MemberRes withDisplay(String displayName, boolean isTreasurer) {
        return new MemberRes(id, userId, role, status, joinedAt, displayName, isTreasurer);
    }
}
