package com.datn.financeapp.module.group.dto.request.transaction;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record GroupTransactionBulkReviewReq(
        @NotNull(message = "Danh sách giao dịch không được để trống.") @NotEmpty(message = "Danh sách giao dịch không được để trống.") List<UUID> transactionIds) {
}
