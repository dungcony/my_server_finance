package com.datn.financeapp.wallet.dto.response;

import java.time.LocalDate;

// {@code stats} lồng trong GET /wallets/{id} (api/02-VI.md mục 3).
public record WalletStatsResponse(
        long transactionCount, long incomeThisMonth, long expenseThisMonth, LocalDate lastTransactionDate) {
}
