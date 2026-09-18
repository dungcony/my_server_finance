package com.datn.financeapp.group.dto.transaction;

import java.util.UUID;

public record GroupTransactionParticipantRes(
        UUID userId,
        Long shareAmount) {
}
