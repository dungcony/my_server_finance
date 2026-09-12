package com.datn.financeapp.user.dto.response;

import com.datn.financeapp.user.enums.RoleName;

import java.util.List;

public record RoleResponse(
        RoleName name,
        Integer level,
        List<PermissionResponse> permissions,
        String desc
) {
}
