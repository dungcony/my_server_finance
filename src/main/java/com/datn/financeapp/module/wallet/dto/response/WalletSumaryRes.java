package com.datn.financeapp.module.wallet.dto.response;

import java.util.UUID;

public record WalletSumaryRes(
        UUID id,
        String name,
        String icon,
        String color
) {
}
