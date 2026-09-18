package com.datn.financeapp.group.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.group.dto.report.GroupBalanceReportRes;
import com.datn.financeapp.group.dto.report.GroupSummaryReportRes;
import com.datn.financeapp.group.service.GroupReportService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups/{groupId}")
@RequiredArgsConstructor
public class GroupReportController {

    private final GroupReportService groupReportService;

    @GetMapping("/summary")
    public ApiResponse<GroupSummaryReportRes> summary(
            @PathVariable UUID groupId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String month) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupReportService.getSummary(userId, groupId, month));
    }

    @GetMapping("/balances")
    public ApiResponse<GroupBalanceReportRes> balances(@PathVariable UUID groupId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupReportService.getBalances(userId, groupId));
    }
}
