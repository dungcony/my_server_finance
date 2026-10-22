package com.datn.financeapp.module.transaction.dto.response;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;

import java.time.Instant;
import java.util.UUID;

/**
 * Một phần tử giao dịch, dùng cho GET /transactions (list/get — plan 03-03) và phản hồi
 * create/update/duplicate ở plan này (api/04-GIAO-DICH.md mục 1 và mục 4).
 */
public record TxnDetailResponse(
        UUID id,
        WalletSumaryRes wallet,
        CateSumaryRes category,
        TxnType type,
        Long amount,
        Instant occurredAt,
        String displayName,
        String note,
        Instant createdAt,
        Instant updatedAt) {

}
