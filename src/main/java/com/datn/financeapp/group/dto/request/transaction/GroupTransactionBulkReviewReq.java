package com.datn.financeapp.group.dto.request.transaction;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record GroupTransactionBulkReviewReq(
        @NotEmpty @JsonAlias({"transaction_ids", "transactionIds"}) List<UUID> transactionIds) {
}
