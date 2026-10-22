package com.datn.financeapp.module.wallet.dto.response;

import com.datn.financeapp.module.wallet.enums.WalletType;

import java.time.Instant;
import java.util.UUID;


public record WalletRes(
        UUID id,
        String name,
        WalletType type,
        Long balance,
        boolean isDefault,
        String icon,
        String color,
        Instant createdAt) {
}
