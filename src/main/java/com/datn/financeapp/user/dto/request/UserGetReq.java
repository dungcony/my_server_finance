package com.datn.financeapp.user.dto.request;

import com.datn.financeapp.user.enums.UserStatus;

import java.util.UUID;

public record UserGetReq(
        UUID id,
        String email,
        boolean deleted
) {

    public UserGetReq(UUID id) {
        this(id, null, false);
    }

    public UserGetReq(String email) {
        this(null, email, false);
    }

}
