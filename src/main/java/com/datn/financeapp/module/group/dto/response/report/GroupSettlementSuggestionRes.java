package com.datn.financeapp.module.group.dto.response.report;

import java.util.UUID;

public record GroupSettlementSuggestionRes(
        UUID fromUserId,
        UUID toUserId,
        Long amount,
        String reason) {
}
