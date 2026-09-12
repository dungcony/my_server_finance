package com.datn.financeapp.user.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;

// Thông tin tóm tắt của User.
public record UserProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        UserPlan plan) {
}
