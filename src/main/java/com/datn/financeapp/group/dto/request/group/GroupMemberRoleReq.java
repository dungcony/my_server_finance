package com.datn.financeapp.group.dto.request.group;

import com.datn.financeapp.group.enums.GroupRole;
import jakarta.validation.constraints.NotNull;

public record GroupMemberRoleReq(
        @NotNull GroupRole role) {
}
