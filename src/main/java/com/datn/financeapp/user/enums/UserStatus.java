package com.datn.financeapp.user.enums;

import jakarta.persistence.Converter;

public enum UserStatus {
    PENDING_VERIFY,
    ACTIVE,
    BLOCKED;

    @Converter(autoApply = true)
    public static class UserStatusConverter extends LowercaseEnumConverter<UserStatus> {
        public UserStatusConverter() {
            super(UserStatus.class);
        }
    }
}
