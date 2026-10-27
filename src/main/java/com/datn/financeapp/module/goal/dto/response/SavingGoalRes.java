package com.datn.financeapp.module.goal.dto.response;

import com.datn.financeapp.module.goal.enums.SavingGoalStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.util.UUID;

public record SavingGoalRes(
        UUID id,
        UUID userId,
        String name,
        SavingGoalStatus status,
        Long targetAmount,
        Long savedAmount,
        String iconCode,
        String note,
        LocalDate targetDate
) {
}
