package com.datn.financeapp.user.dto.response;

import com.datn.financeapp.user.enums.PermissionName;

public record PermissionResponse(
        PermissionName name,
        String desc
) {
}
