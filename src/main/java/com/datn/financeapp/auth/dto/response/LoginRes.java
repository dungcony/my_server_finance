package com.datn.financeapp.auth.dto.response;

import com.datn.financeapp.user.dto.response.UserRes;

public record LoginRes(
        UserRes user,
        TokenRes token

) {
}
