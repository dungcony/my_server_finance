package com.datn.financeapp.group.dto.report;

import java.util.UUID;

public record GroupSettlementSuggestionRes(
        UUID fromUserId,
        UUID toUserId,
        Long amount,
        String reason) {
}
