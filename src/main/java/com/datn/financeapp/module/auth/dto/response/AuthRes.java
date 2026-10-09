package com.datn.financeapp.module.auth.dto.response;

import com.datn.financeapp.module.user.dto.response.UserRes;

public record AuthRes(
        UserRes user,
        TokenRes token
) {
}
