package com.datn.financeapp.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum RoleName {
    ROLE_ADMIN,
    ROLE_USER;

    @JsonCreator
    public static RoleName fromString(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        return RoleName.valueOf(normalized);
    }
}
