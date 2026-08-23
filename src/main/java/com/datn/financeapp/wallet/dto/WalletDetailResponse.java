package com.datn.financeapp.wallet.dto;

import java.time.Instant;
import java.util.UUID;

/** GET /wallets/{id} (api/02-VI.md mục 3) — thêm initialBalance + stats so với WalletResponse. */
public record WalletDetailResponse(
        UUID id,
        String name,
        String type,
        Long initialBalance,
        Long currentBalance,
        Boolean includeInTotal,
        boolean isShared,
        UUID groupId,
        String icon,
        String color,
        Integer sortOrder,
        WalletStatsDto stats,
        Instant createdAt) {
}
