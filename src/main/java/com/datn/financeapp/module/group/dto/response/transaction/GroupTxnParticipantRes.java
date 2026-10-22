package com.datn.financeapp.module.group.dto.response.transaction;

import java.util.UUID;

public record GroupTxnParticipantRes(
        UUID userId,
        Long shareAmount) {
}
