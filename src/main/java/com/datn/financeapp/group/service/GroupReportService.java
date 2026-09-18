package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.report.GroupBalanceReportRes;
import com.datn.financeapp.group.dto.report.GroupSummaryReportRes;
import java.util.UUID;

public interface GroupReportService {

    GroupSummaryReportRes getSummary(UUID userId, UUID groupId, String month);

    default GroupSummaryReportRes getSummary(UUID userId, UUID groupId) {
        return getSummary(userId, groupId, null);
    }

    GroupBalanceReportRes getBalances(UUID userId, UUID groupId);
}
