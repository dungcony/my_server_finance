package com.datn.financeapp.module.group.dto.response.transaction;

import java.util.UUID;

public record GroupTransactionParticipantRes(
        UUID userId,
        Long shareAmount) {
}
