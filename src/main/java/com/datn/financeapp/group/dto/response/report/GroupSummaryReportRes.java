package com.datn.financeapp.group.dto.response.report;

import com.datn.financeapp.group.dto.response.wallet.GroupWalletRes;
import java.util.UUID;

public record GroupSummaryReportRes(
        UUID groupId,
        String groupName,
        Long target,
        String period,
        GroupWalletRes fund,
        Long totalExpense,
        Long totalContribution) {
}
