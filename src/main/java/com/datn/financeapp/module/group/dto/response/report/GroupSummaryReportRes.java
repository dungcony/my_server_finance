package com.datn.financeapp.module.group.dto.response.report;

import com.datn.financeapp.module.group.dto.response.fund.FundRes;

import java.util.UUID;

public record GroupSummaryReportRes(
        UUID groupId,
        String groupName,
        Long target,
        String period,
        FundRes fund,
        Long totalExpense,
        Long totalContribution) {
}
