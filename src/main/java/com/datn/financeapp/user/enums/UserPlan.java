package com.datn.financeapp.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum UserPlan {
    PREMIUM,
    FREE;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static UserPlan fromValue(String value) {
        if (value == null) return null;
        return UserPlan.valueOf(value.toUpperCase());
    }

    @jakarta.persistence.Converter(autoApply = true)
    public static class Converter implements AttributeConverter<UserPlan, String> {
        @Override
        public String convertToDatabaseColumn(UserPlan plan) {
            return plan != null ? plan.name().toLowerCase() : null;
        }

        @Override
        public UserPlan convertToEntityAttribute(String dbData) {
            return dbData != null ? UserPlan.valueOf(dbData.toUpperCase()) : null;
        }
    }
}
