package com.datn.financeapp.module.user.dto.request;

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

    public UserGetReq(String email, boolean isDeleted) {
        this(null, email, isDeleted);
    }

}
