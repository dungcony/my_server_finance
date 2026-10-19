package com.datn.financeapp.module.transaction.dto.response;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.transaction.enums.FrequencyType;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Phản hồi chi tiết giao dịch định kỳ.
 */
public record RecTxnRes(
        UUID id,
        String displayName,
        TxnType type,
        Long amount,
        UUID userId,
        WalletSumaryRes wallet,
        CateSumaryRes category,
        LocalDate startDate,
        LocalDate endDate,
        FrequencyType frequencyType,
        Integer interval,
        Boolean isEnabled,
        String note
) {
}
