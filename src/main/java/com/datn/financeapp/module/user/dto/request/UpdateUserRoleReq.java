package com.datn.financeapp.module.user.dto.request;

import com.datn.financeapp.module.user.enums.RoleName;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateUserRoleReq(
        @NotNull(message = "không được để trống người dùng")
        UUID userId,
        @NotNull(message = "Vai trò không được để trống.")
        RoleName roleName) {

}
