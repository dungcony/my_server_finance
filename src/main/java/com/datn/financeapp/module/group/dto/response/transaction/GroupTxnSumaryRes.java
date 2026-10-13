package com.datn.financeapp.module.group.dto.response.transaction;

import com.datn.financeapp.module.group.enums.GroupTxnType;

import java.util.UUID;

public record GroupTxnSumaryRes(
        UUID txnId,
        UUID transactorId,
        UUID createdBy,
        String categoryCode,
        GroupTxnType type,
        Long amount
) {
}
