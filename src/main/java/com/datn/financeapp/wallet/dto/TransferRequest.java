package com.datn.financeapp.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/** Body của POST /wallets/transfer (api/02-VI.md mục 8). */
public record TransferRequest(
        @NotNull UUID sourceWalletId,
        @NotNull UUID destinationWalletId,
        @NotNull @Positive Long amount,
        LocalDate date,
        String note,
        Boolean failIfInsufficient) {
}
