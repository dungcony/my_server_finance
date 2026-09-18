package com.datn.financeapp.group.dto.wallet;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GroupWalletCreateReq(
        @NotBlank @Size(max = 50) String name,
        @NotNull UUID heldByUserId,
        @NotNull @Min(0) Long initialBalance) {
}
