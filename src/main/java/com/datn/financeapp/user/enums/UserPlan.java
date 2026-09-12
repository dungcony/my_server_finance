package com.datn.financeapp.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
    public static class Converter extends LowercaseEnumConverter<UserPlan> {
        public Converter() { super(UserPlan.class); }
    }
}
