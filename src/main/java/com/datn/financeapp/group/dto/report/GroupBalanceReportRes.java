package com.datn.financeapp.group.dto.report;

import java.util.List;
import java.util.UUID;

public record GroupBalanceReportRes(
        UUID groupId,
        Long target,
        Long fundBalance,
        Long totalNeededContribution,
        Boolean isSettlementEnabled,
        List<GroupBalanceItemRes> balances) {
}
