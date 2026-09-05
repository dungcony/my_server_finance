package com.datn.financeapp.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * GET /wallets/{id} (api/02-VI.md mục 3) — thêm initialBalance + stats so với WalletResponse.
 *
 * <p>D-37/TXN-09 — cùng ý nghĩa {@code currentBalance}/{@code projectedBalance} như
 * {@link WalletResponse}: {@code currentBalance} đã trừ ngược, {@code projectedBalance} chỉ trả
 * khi ví có giao dịch tương lai.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        WalletStatsResponse stats,
        Instant createdAt,
        Long projectedBalance) {
}
