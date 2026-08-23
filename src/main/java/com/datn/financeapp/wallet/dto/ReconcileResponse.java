package com.datn.financeapp.wallet.dto;

import java.util.UUID;

/** Phản hồi 200 của POST /wallets/{id}/reconcile (api/02-VI.md mục 10). */
public record ReconcileResponse(
        UUID walletId,
        Long storedBalance,
        Long computedBalance,
        Long difference,
        boolean matches,
        boolean wasFixed) {
}
