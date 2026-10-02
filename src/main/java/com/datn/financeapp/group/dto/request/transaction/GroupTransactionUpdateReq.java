package com.datn.financeapp.group.dto.request.transaction;

import com.datn.financeapp.group.enums.MoneySource;
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
        @Positive @Max(999999999999L) Long amount,
        Instant occurredAt,
        LocalDate date,
        MoneySource moneySource,
        UUID categoryId,
        UUID transactorId,
        @Size(max = 255) String note,
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
