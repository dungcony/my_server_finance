package com.datn.financeapp.module.wallet.dto.response;

import com.datn.financeapp.module.wallet.enums.WalletType;

import java.util.UUID;

public record WalletSumaryRes(
        UUID id,
        String name,
        WalletType type,
        String icon,
        String color
) {
    public WalletSumaryRes(UUID id, String name, String icon, String color) {
        this(id, name, null, icon, color);
    }
}
