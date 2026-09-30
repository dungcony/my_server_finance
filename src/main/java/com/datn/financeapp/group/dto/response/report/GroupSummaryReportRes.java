package com.datn.financeapp.group.dto.response.report;

import com.datn.financeapp.group.dto.response.fund.GroupFundRes;
import java.util.UUID;

public record GroupSummaryReportRes(
        UUID groupId,
        String groupName,
        Long target,
        String period,
        GroupFundRes fund,
        Long totalExpense,
        Long totalContribution) {
}
