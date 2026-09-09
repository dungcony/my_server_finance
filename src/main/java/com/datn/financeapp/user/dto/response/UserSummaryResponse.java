package com.datn.financeapp.user.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;

// Thông tin tóm tắt của User.
public record UserSummaryResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        UserPlan plan,
        UserStatus status,
        @JsonIgnore String password,
        @JsonIgnore String googleId) {

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
