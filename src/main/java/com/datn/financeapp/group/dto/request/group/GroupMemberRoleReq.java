package com.datn.financeapp.group.dto.request.group;

import com.datn.financeapp.group.enums.MemberRole;
import jakarta.validation.constraints.NotNull;

public record GroupMemberRoleReq(
        @NotNull MemberRole role) {
}
