package com.datn.financeapp.module.goal.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record ContributionGoalRes(
        UUID id,
        UUID goalId,
        Long amount,
        LocalDate contributedDate,
        String note
) {
}