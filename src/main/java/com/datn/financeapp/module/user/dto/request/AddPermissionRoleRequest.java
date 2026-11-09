package com.datn.financeapp.module.user.dto.request;

import com.datn.financeapp.module.user.enums.PermissionName;
import com.datn.financeapp.module.user.enums.RoleName;

public record AddPermissionRoleRequest(
        RoleName roleName,
        PermissionName permissionName
) {
}
