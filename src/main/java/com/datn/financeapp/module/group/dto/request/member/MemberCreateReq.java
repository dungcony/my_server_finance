package com.datn.financeapp.module.group.dto.request.member;

import com.datn.financeapp.module.group.enums.MemberRole;
import com.datn.financeapp.module.group.enums.MemberStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MemberCreateReq(
        @NotNull UUID groupId,
        @NotNull UUID userId,
        @NotNull MemberRole role,
        @NotNull MemberStatus status
) {

}
