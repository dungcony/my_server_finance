package com.datn.financeapp.group.dto.wallet;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GroupWalletReconcileReq(
        @NotNull Long actualBalance,
        @NotNull LocalDate date,
        @Size(max = 255) String note,
        List<UUID> excludedUserIds) {
}
