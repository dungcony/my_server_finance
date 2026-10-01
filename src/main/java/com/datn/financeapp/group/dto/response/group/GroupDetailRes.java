package com.datn.financeapp.group.dto.response.group;

import com.datn.financeapp.group.dto.response.fund.FundRes;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.GroupStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupDetailRes(
        UUID id,
        String name,
        String description,
        GroupStatus status,
        String inviteCode,
        Long target,
        Boolean isSettlementEnabled,
        Boolean isJoinWithoutConfirm,
        Instant createdAt,
        MemberRole myRole,
        FundRes fund,
        List<MemberRes> members) {
}
