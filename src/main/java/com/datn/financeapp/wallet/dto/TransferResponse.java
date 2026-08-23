package com.datn.financeapp.wallet.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Phản hồi 201 của POST /wallets/transfer (api/02-VI.md mục 8). */
public record TransferResponse(TransactionInfo transaction, NewBalance newBalance) {

    public record TransactionInfo(
            UUID id,
            String type,
            Long amount,
            LocalDate date,
            WalletRef sourceWallet,
            WalletRef destinationWallet,
            String note) {
    }

    public record WalletRef(UUID id, String name) {
    }

    public record NewBalance(Long sourceWallet, Long destinationWallet) {
    }
}
