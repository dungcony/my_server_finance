package com.datn.financeapp.group.dto.transaction;

import com.datn.financeapp.group.enums.MoneySource;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record GroupTransactionUpdateReq(
        @NotNull @Positive @Max(999999999999L) Long amount,
        @JsonAlias({"occurred_at", "occurredAt"}) Instant occurredAt,
        LocalDate date,
        @JsonAlias({"money_source", "moneySource"}) MoneySource moneySource,
        @JsonAlias({"category_id", "categoryId"}) UUID categoryId,
        @JsonAlias({"group_wallet_id", "groupWalletId", "wallet_id", "walletId"}) UUID groupWalletId,
        @NotNull @JsonAlias({"user_id", "userId"}) UUID userId,
        @Size(max = 255) String note,
        @JsonAlias({"personal_transaction_id", "personalTransactionId"}) UUID personalTransactionId,
        @Valid List<GroupTransactionParticipantReq> participants) {

    public Instant resolveOccurredAt() {
        if (occurredAt != null) {
            return occurredAt;
        }
        if (date != null) {
            return date.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        }
        return Instant.now();
    }
}
