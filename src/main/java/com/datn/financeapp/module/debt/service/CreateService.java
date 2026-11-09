package com.datn.financeapp.module.debt.service;

import com.datn.financeapp.common.security.SecurityContextUtil;

import java.util.UUID;

public interface CreateService<REQ, RES> {
    RES create(UUID userId, REQ req);

    default RES create(REQ req) {
        return create(SecurityContextUtil.currentUserId(), req);
    }
}
