package com.datn.financeapp.report.service;

import com.datn.financeapp.report.dto.response.CategoryBreakdownResponse;
import com.datn.financeapp.report.dto.response.CategoryGroupBreakdownResponse;
import com.datn.financeapp.report.dto.response.DailyTrendResponse;
import com.datn.financeapp.report.dto.response.MonthlyTrendResponse;
import com.datn.financeapp.report.dto.response.ReportHomeResponse;
import com.datn.financeapp.report.dto.response.ReportSummaryResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

// Public API của module Report cho các báo cáo thống kê và biểu đồ xu hướng.
public interface ReportQueryService {

    ReportSummaryResponse summary(
            UUID userId, String period, LocalDate fromDate, LocalDate toDate, UUID walletId);

    CategoryGroupBreakdownResponse byCategoryGroup(
            UUID userId, String period, LocalDate fromDate, LocalDate toDate, String type, UUID walletId);

    CategoryBreakdownResponse byCategory(
            UUID userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            String type,
            String level,
            int limit,
            UUID walletId);

    DailyTrendResponse dailyTrend(UUID userId, YearMonth month, UUID walletId);

    MonthlyTrendResponse monthlyTrend(UUID userId, int monthsCount);

    ReportHomeResponse home(UUID userId, String period, UUID walletId);
}
