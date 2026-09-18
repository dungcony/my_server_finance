package com.datn.financeapp.group.dto.report;

import com.datn.financeapp.group.dto.wallet.GroupWalletRes;
import java.util.List;
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
