package com.datn.financeapp.group.dto.request.transaction;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GroupTransactionParticipantReq(
        @NotNull UUID userId,
        Long shareAmount) {
}
