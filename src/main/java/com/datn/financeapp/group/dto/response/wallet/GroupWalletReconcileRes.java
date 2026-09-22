package com.datn.financeapp.group.dto.response.wallet;

import com.datn.financeapp.group.enums.GroupTransactionType;
import java.util.UUID;

public record GroupWalletReconcileRes(
        UUID walletId,
        Long previousBalance,
        Long actualBalance,
        Long difference,
        GroupTransactionType adjustmentType,
        UUID transactionId) {
}
