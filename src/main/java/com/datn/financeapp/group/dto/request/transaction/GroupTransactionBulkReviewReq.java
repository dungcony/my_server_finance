package com.datn.financeapp.group.dto.request.transaction;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record GroupTransactionBulkReviewReq(
        @NotNull @NotEmpty List<UUID> transactionIds) {
}
