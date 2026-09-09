package com.datn.financeapp.user.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;

// GET /auth/me hoặc GET /users/me — hồ sơ đầy đủ kèm thống kê.
public record UserDetailResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        UserPlan plan,
        UserStatus status,
        @JsonIgnore String password,
        @JsonIgnore String googleId,
        Instant createdAt,
        Instant lastLoginAt,
        UserStatsResponse stats) {

    @JsonProperty("username")
    public String username() {
        if (firstName == null && lastName == null) {
            return email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        }
        if (lastName == null) return firstName;
        if (firstName == null) return lastName;
        return (lastName + " " + firstName).trim();
    }

    @JsonProperty("has_password")
    public boolean hasPassword() {
        return password != null;
    }

    @JsonProperty("google_linked")
    public boolean googleLinked() {
        return googleId != null;
    }

    @JsonProperty("is_confirm")
    public boolean isConfirm() {
        return status == UserStatus.ACTIVE;
    }

    @JsonProperty("role")
    public String role() {
        return "USER";
    }
}
