package com.datn.financeapp.module.wallet.dto.response;

import java.util.UUID;

// Phản hồi 200 của POST /wallets/{id}/reconcile (api/02-VI.md mục 10).
public record ReconcileRes(
        WalletSumaryRes walletId,
        Long toltalTxn,
        Long walletBalance
) {
}
