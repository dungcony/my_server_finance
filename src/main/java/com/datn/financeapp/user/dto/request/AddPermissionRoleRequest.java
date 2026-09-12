package com.datn.financeapp.user.dto.request;

import com.datn.financeapp.user.enums.PermissionName;
import com.datn.financeapp.user.enums.RoleName;

public record AddPermissionRoleRequest(
        RoleName roleName,
        PermissionName permissionName
) {
}
