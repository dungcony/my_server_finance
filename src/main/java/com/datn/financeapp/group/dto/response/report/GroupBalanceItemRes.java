package com.datn.financeapp.group.dto.response.report;

import java.util.UUID;

public record GroupBalanceItemRes(
        UUID userId,
        String fullName,
        String status,
        Long totalContributed,
        Long totalPaidOutOfPocket,
        Long totalRefunded,
        Long totalWithdrawn,
        Long totalShareAmount,
        Long netBalance,
        Long neededContribution) {
}
