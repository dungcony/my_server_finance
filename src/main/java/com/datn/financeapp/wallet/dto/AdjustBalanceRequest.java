package com.datn.financeapp.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body của POST /wallets/{id}/adjust-balance (api/02-VI.md mục 9). */
public record AdjustBalanceRequest(
        @NotNull Long actualBalance,
        Boolean countsInReport,
        @Size(max = 200) String note) {
}
