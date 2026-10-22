package com.datn.financeapp.module.user.dto.response;

import com.datn.financeapp.module.user.enums.RoleName;

import java.util.List;

public record RoleResponse(
        RoleName name,
        Integer level,
        List<PermissionResponse> permissions,
        String desc
) {
}
