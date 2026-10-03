package com.datn.financeapp.auth.service;

import com.datn.financeapp.auth.dto.request.TokenCreateReq;
import com.datn.financeapp.auth.dto.response.TokenRes;
import com.datn.financeapp.auth.entity.RefreshToken;

import java.util.UUID;

public interface TokenService {
    TokenRes create(TokenCreateReq req);

    UUID checkRefreshAndGetUserId(String refresh);

    RefreshToken revokeRefresh(String refresh);

    int revokeAllByUserId(UUID userId);

    int revokeAllByEmail(String email);
}
