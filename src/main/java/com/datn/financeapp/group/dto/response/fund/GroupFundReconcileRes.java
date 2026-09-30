package com.datn.financeapp.group.dto.response.fund;

import com.datn.financeapp.group.enums.TransactionType;

import java.util.UUID;

public record GroupFundReconcileRes(
        UUID fundId,
        Long previousBalance,
        Long actualBalance,
        Long difference,
        TransactionType adjustmentType,
        UUID transactionId) {
}
