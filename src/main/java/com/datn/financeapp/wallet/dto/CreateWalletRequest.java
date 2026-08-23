package com.datn.financeapp.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body của POST /wallets (api/02-VI.md mục 4). */
public record CreateWalletRequest(
        @NotBlank @Size(min = 1, max = 50) String name,
        @NotBlank @Pattern(regexp = "^(cash|bank|e_wallet|credit_card)$") String type,
        @NotNull Long initialBalance,
        Boolean includeInTotal,
        String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
        UUID groupId) {
}
