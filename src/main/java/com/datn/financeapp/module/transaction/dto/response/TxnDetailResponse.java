package com.datn.financeapp.module.transaction.dto.response;

import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một phần tử giao dịch, dùng cho GET /transactions (list/detail — plan 03-03) và phản hồi
 * create/update/duplicate ở plan này (api/04-GIAO-DICH.md mục 1 và mục 4).
 */
public record TxnDetailResponse(
        UUID id,
        WalletRes wallet,
        CategoryRes category,
        TxnType type,
        Long amount,
        Instant occruredAt,
        String displayName,
        String note,
        Instant createdAt,
        Instant updatedAt) {

}
