package com.datn.financeapp.module.group.dto.response.fund;

import com.datn.financeapp.module.group.enums.GTransactionType;

import java.util.UUID;

public record GroupFundReconcileRes(
        Long previousBalance,
        Long actualBalance,
        Long difference,
        GTransactionType adjustmentType,
        UUID transactionId) {
}
