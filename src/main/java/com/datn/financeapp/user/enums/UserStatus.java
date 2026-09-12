package com.datn.financeapp.user.enums;

public enum UserStatus {
    PENDING_VERIFY,
    ACTIVE,
    BLOCKED;

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter extends LowercaseEnumConverter<UserStatus> {
        public Converter() { super(UserStatus.class); }
    }
}
