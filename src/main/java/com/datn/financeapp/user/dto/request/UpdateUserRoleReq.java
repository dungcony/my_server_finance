package com.datn.financeapp.user.dto.request;

import com.datn.financeapp.user.enums.RoleName;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateUserRoleReq(
        @NotNull(message = "không được để trống người dùng")
        UUID userId,
        @NotNull(message = "không được để trống người dùng")
        RoleName roleName) {

}
