package com.datn.financeapp.module.goal.dto.response;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.goal.enums.SavingGoalStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SavingGoalListRes(
        List<SavingGoalSummayRes> goals,
        PageMeta pageMeta
) {
    public record SavingGoalSummayRes(
            UUID id,
            String name,
            SavingGoalStatus status,
            Long targetAmount,
            Long savedAmount,
            String iconCode,
            LocalDate targetDate
    ) {
    }
}