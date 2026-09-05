package com.datn.financeapp.transaction.dto.response;

import java.util.UUID;

/** Phản hồi 200 của DELETE /transactions/{id} (api/04-GIAO-DICH.md mục 9). */
public record DeleteTransactionResponse(NewBalance newBalance) {

    public record NewBalance(UUID walletId, Long balance) {
    }
}
