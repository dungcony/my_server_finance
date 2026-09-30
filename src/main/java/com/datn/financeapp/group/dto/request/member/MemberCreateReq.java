package com.datn.financeapp.group.dto.request.member;

import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MemberCreateReq(
        @NotNull UUID groupId,
        @NotNull UUID userId,
        @NotNull MemberRole role,
        @NotNull MemberStatus status
) {

}
