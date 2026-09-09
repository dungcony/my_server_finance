package com.datn.financeapp.user.dto.response;

public record UserStatsResponse(
        long walletCount,
        long transactionCount,
        long groupCount) {
}
