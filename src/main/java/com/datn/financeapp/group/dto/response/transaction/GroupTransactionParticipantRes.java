package com.datn.financeapp.group.dto.response.transaction;

import java.util.UUID;

public record GroupTransactionParticipantRes(
        UUID userId,
        Long shareAmount) {
}
