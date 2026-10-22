package com.datn.financeapp.module.user.dto.response;

import com.datn.financeapp.module.user.enums.PermissionName;

public record PermissionResponse(
        PermissionName name,
        String desc
) {
}
