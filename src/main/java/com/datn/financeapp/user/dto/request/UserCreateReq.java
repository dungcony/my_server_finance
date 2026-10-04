package com.datn.financeapp.user.dto.request;

import com.datn.financeapp.user.enums.UserStatus;

import java.time.Instant;

public record UserCreateReq(
                String email,
                String password,
                String googleId,
                String firstName,
                String lastName,
                String avatarUrl,
                UserStatus status,
                Instant createdAt) {

        public static UserCreateReq forGoogle(
                        String email,
                        String googleId,
                        Instant now) {
                return new UserCreateReq(
                                email,
                                null,
                                googleId,
                                null,
                                null,
                                null,
                                UserStatus.ACTIVE,
                                now);
        }

        public static UserCreateReq forEmail(
                        String email,
                        String password,
                        Instant now) {
                return new UserCreateReq(
                                email,
                                password,
                                null,
                                null,
                                null,
                                null,
                                UserStatus.PENDING_VERIFY,
                                now);
        }
}
