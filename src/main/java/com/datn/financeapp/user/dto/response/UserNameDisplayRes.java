package com.datn.financeapp.user.dto.response;

import com.datn.financeapp.user.enums.UserStatus;

import java.util.UUID;

public record UserNameDisplayRes(
        UUID id,
        String firstName,
        String lastName,
        UserStatus status,
        boolean deleted) {
    public String fullName() {
        if (deleted || status == UserStatus.BLOCKED)
            return "Người sử dụng app";

        if (firstName == null && lastName == null) {
            return "";
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }
}
