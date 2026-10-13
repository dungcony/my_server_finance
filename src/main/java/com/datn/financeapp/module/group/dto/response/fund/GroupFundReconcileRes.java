package com.datn.financeapp.module.group.dto.response.fund;

import com.datn.financeapp.module.group.enums.GroupTxnType;

import java.util.UUID;

public record GroupFundReconcileRes(
        Long previousBalance,
        Long actualBalance,
        Long difference,
        GroupTxnType adjustmentType,
        UUID transactionId) {
}
