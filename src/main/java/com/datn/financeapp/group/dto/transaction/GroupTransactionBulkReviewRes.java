package com.datn.financeapp.group.dto.transaction;

import java.util.List;
import java.util.UUID;

public record GroupTransactionBulkReviewRes(
        int totalRequested,
        int successCount,
        int failedCount,
        List<UUID> processedIds) {
}
