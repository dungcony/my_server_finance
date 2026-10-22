package com.datn.financeapp.module.auth.service;

import com.datn.financeapp.module.auth.dto.request.TokenCreateReq;
import com.datn.financeapp.module.auth.dto.response.TokenRes;
import com.datn.financeapp.module.auth.entity.RefreshToken;

import java.util.UUID;

public interface TokenService {
    TokenRes create(TokenCreateReq req);

    UUID checkRefreshAndGetUserId(String refresh);

    RefreshToken revokeRefresh(String refresh);

    int revokeAllByUserId(UUID userId);

    int revokeAllByEmail(String email);
}
