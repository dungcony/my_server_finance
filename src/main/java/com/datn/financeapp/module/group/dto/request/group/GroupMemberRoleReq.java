package com.datn.financeapp.module.group.dto.request.group;

import com.datn.financeapp.module.group.enums.MemberRole;
import jakarta.validation.constraints.NotNull;

public record GroupMemberRoleReq(
        @NotNull MemberRole role) {
}
