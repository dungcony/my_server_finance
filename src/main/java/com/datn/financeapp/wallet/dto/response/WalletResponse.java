package com.datn.financeapp.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Một phần tử của GET /wallets (api/02-VI.md mục 1).
 *
 * <p>D-37/TXN-09 — {@code currentBalance} ở đây là trường API "tiền thật đến hết hôm nay" (đã
 * trừ ngược giao dịch tương lai), KHÔNG map thẳng cột {@code wallets.current_balance}.
 * {@code projectedBalance} bằng đúng cột thô, chỉ khác NULL khi ví có giao dịch tương lai —
 * {@code @JsonInclude(NON_NULL)} để Jackson bỏ hẳn trường thay vì trả {@code null} tường minh
 * (tránh app hiểu nhầm "có nhưng bằng 0").
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        Instant createdAt,
        Long projectedBalance) {
}
