package com.datn.financeapp.module.wallet.dto.response;

import com.datn.financeapp.module.icon.dto.response.IconResponse;

import java.util.UUID;

public record WalletSumaryRes(
        UUID id,
        String name,
        String icon
) {
}
