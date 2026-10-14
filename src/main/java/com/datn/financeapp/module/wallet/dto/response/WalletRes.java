package com.datn.financeapp.module.wallet.dto.response;

import com.datn.financeapp.module.wallet.enums.WalletType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;


public record WalletRes(
        UUID id,
        String name,
        WalletType type,
        Long balance,
        boolean isDefault,
        UUID iconId,
        String icon,
        String color,
        Instant createdAt) {
}
