package com.datn.financeapp.group.dto.response.report;

import java.util.UUID;

public record GroupBalanceItemRes(
        UUID userId,
        String fullName,
        String status,
        Long totalPaidOutOfPocket,
        Long totalRefunded,
        Long totalShareAmount,
        Long netBalance,
        Long neededContribution) {
}
