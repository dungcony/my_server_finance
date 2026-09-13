package com.datn.financeapp.wallet.dto.response;

import java.util.List;

/**
 * GET /wallets/summary (api/02-VI.md mục 2). {@code personalTotal} và {@code sharedTotal}
 * KHÔNG cộng đôi — tách riêng để tránh đếm ví chung vào tổng tài sản cá nhân của từng thành
 * viên nhóm (xem giải thích trong api/02-VI.md).
 */
public record WalletSummaryResponse(
        long personalTotal,
        long personalWalletCount,
        long sharedTotal,
        long sharedWalletCount,
        List<ByTypeItem> byType) {

    public record ByTypeItem(String type, long total, long walletCount) {
    }
}
