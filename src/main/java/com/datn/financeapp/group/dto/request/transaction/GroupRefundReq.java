package com.datn.financeapp.group.dto.request.transaction;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record GroupRefundReq(
        @NotNull @JsonAlias({"to_user_id", "toUserId", "user_id", "userId"}) UUID toUserId,
        @NotNull @Positive @Max(999999999999L) Long amount,
        @Size(max = 255) String note,
        @JsonAlias({"occurred_at", "occurredAt"}) Instant occurredAt) {

    public Instant resolveOccurredAt() {
        return (occurredAt != null) ? occurredAt : Instant.now();
    }
}
