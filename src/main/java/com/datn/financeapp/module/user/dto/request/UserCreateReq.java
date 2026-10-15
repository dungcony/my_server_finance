package com.datn.financeapp.module.user.dto.request;

import com.datn.financeapp.module.user.enums.UserStatus;

import java.time.Instant;

public record UserCreateReq(
        String email,
        String password,
        String googleId,
        String firstName,
        String lastName,
        String avatarUrl,
        UserStatus status) {

    public static UserCreateReq forGoogle(
            String email,
            String googleId) {
        return new UserCreateReq(
                email,
                null,
                googleId,
                null,
                null,
                null,
                UserStatus.ACTIVE);
    }

    public static UserCreateReq forEmail(
            String email,
            String password) {
        return new UserCreateReq(
                email,
                password,
                null,
                null,
                null,
                null,
                UserStatus.PENDING_VERIFY);
    }
}
