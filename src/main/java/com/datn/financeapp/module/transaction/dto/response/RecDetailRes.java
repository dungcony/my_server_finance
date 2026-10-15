package com.datn.financeapp.module.transaction.dto.response;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.transaction.enums.FrequencyType;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi chi tiết giao dịch định kỳ.
 */
public record RecDetailRes(
        @JsonIgnore UUID id,
        String displayName,
        TxnType type,
        Long amount,
        WalletSumaryRes wallet,
        CateSumaryRes category,
        Instant startDate,
        Instant endDate,
        FrequencyType frequencyType,
        Integer interval,
        Boolean isEnabled,
        String note
) {
}
