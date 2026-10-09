package com.datn.financeapp.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
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

    @Converter(autoApply = true)
    public static class UserPlanConverter extends LowercaseEnumConverter<UserPlan> {
        public UserPlanConverter() { super(UserPlan.class); }
    }
}
