package com.datn.financeapp.auth.dto.response;

import com.datn.financeapp.user.dto.response.UserRes;

public record AuthRes(
        UserRes user,
        TokenRes token
) {
}
