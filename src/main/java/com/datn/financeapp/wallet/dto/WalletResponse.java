package com.datn.financeapp.wallet.dto;

import java.time.Instant;
import java.util.UUID;

/** Một phần tử của GET /wallets (api/02-VI.md mục 1). */
public record WalletResponse(
        UUID id,
        String name,
        String type,
        Long currentBalance,
        Boolean includeInTotal,
        boolean isShared,
        UUID groupId,
        String icon,
        String color,
        Integer sortOrder,
        Instant createdAt) {
}
