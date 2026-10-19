package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.module.transaction.dto.response.RecTxnRes;

import java.util.UUID;

public interface RecTxnService {
    RecTxnRes getById(UUID recId, boolean includeDeleted);
}
