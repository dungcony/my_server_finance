package com.datn.financeapp.module.group.dto.request.member;

import com.datn.financeapp.module.group.enums.MemberRole;
import com.datn.financeapp.module.group.enums.MemberStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MemberCreateReq(
        @NotNull(message = "Nhóm không được để trống.") UUID groupId,
        @NotNull(message = "Người dùng không được để trống.") UUID userId,
        @NotNull(message = "Vai trò không được để trống.") MemberRole role,
        @NotNull(message = "Trạng thái không được để trống.") MemberStatus status
) {

}
