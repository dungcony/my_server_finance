package com.datn.financeapp.wallet.dto.response;

import java.util.UUID;

/** Phản hồi 200 của POST /wallets/{id}/adjust-balance (api/02-VI.md mục 9). */
public record AdjustBalanceResponse(
        UUID walletId,
        Long previousBalance,
        Long actualBalance,
        Long difference,
        boolean adjusted,
        UUID transactionId,
        String transactionType,
        Boolean countsInReport) {
}
