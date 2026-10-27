package com.datn.financeapp.module.goal.dto.response;

import com.datn.financeapp.common.response.PageMeta;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ContributionGoalListRes(
        List<ContributionGoalRes> contributions,
        PageMeta pageMeta
) {
}