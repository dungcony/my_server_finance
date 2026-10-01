package com.datn.financeapp.user.dto.response;

import java.util.UUID;

import com.datn.financeapp.user.enums.UserPlan;

// Thông tin tóm tắt của User.
public record UserProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        UserPlan plan,
        boolean hasPassword) {
}
