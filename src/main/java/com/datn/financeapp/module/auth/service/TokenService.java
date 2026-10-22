package com.datn.financeapp.module.auth.service;

import com.datn.financeapp.module.auth.dto.request.TokenCreateReq;
import com.datn.financeapp.module.auth.dto.response.TokenRes;

import java.util.UUID;

public interface TokenService {
    TokenRes create(TokenCreateReq req);

    UUID checkRefreshAndGetUserId(String refresh);

    void revokeRefresh(String refresh);

    void revokeAllByUserId(UUID userId);

    void revokeAllByEmail(String email);
}
