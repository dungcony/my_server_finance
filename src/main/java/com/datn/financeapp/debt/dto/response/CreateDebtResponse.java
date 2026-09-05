package com.datn.financeapp.debt.dto.response;

import java.util.UUID;

/** Body phản hồi 201 của {@code POST /debts} (api/08 mục 4). */
public record CreateDebtResponse(
        DebtListItemResponse debt, OriginTransaction originTransaction, NewBalance newBalance) {

    public record OriginTransaction(UUID id, String type, Long amount) {}

    public record NewBalance(UUID walletId, Long balance) {}
}
