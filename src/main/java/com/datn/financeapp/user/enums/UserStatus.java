package com.datn.financeapp.user.enums;

import jakarta.persistence.AttributeConverter;

public enum UserStatus {
    PENDING_VERIFY,
    ACTIVE,
    BLOCKED;

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter implements AttributeConverter<UserStatus, String> {
        @Override
        public String convertToDatabaseColumn(UserStatus plan) {
            return plan != null ? plan.name().toLowerCase() : null;
        }

        @Override
        public UserStatus convertToEntityAttribute(String dbData) {
            return dbData != null ? UserStatus.valueOf(dbData.toUpperCase()) : null;
        }
    }
}
