package com.datn.financeapp.report.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.report.dto.CategoryBreakdownResponse;
import com.datn.financeapp.report.dto.CategoryGroupBreakdownResponse;
import com.datn.financeapp.report.dto.DailyTrendResponse;
import com.datn.financeapp.report.dto.MonthlyTrendResponse;
import com.datn.financeapp.report.dto.ReportHomeResponse;
import com.datn.financeapp.report.dto.ReportSummaryResponse;
import com.datn.financeapp.report.service.ReportQueryService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 6 điểm cuối báo cáo chỉ đọc (REPORT-01..04, api/06-BAO-CAO.md mục 1-6). Điểm cuối xuất báo cáo
 * ({@code POST /reports/export}, mục 7) thêm ở Task 3 của plan này (D-59).
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportQueryService reportQueryService;

    @GetMapping("/home")
    public ApiResponse<ReportHomeResponse> home(
            @RequestParam(required = false, defaultValue = "month") String period) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(reportQueryService.home(userId, period));
    }

    @GetMapping("/summary")
    public ApiResponse<ReportSummaryResponse> summary(
            @RequestParam(required = false) String period,
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(name = "wallet_id", required = false) UUID walletId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(reportQueryService.summary(userId, period, fromDate, toDate, walletId));
    }

    @GetMapping("/by-category-group")
    public ApiResponse<CategoryGroupBreakdownResponse> byCategoryGroup(
            @RequestParam(required = false) String period,
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "expense") String type) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(reportQueryService.byCategoryGroup(userId, period, fromDate, toDate, type));
    }

    @GetMapping("/by-category")
    public ApiResponse<CategoryBreakdownResponse> byCategory(
            @RequestParam(required = false) String period,
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "expense") String type,
            @RequestParam(required = false, defaultValue = "parent") String level,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(reportQueryService.byCategory(userId, period, fromDate, toDate, type, level, limit));
    }

    @GetMapping("/daily-trend")
    public ApiResponse<DailyTrendResponse> dailyTrend(
            @RequestParam(required = false) String month) {
        UUID userId = SecurityContextUtil.currentUserId();
        YearMonth ym = month == null ? null : YearMonth.parse(month);
        return ApiResponse.of(reportQueryService.dailyTrend(userId, ym));
    }

    @GetMapping("/monthly-trend")
    public ApiResponse<MonthlyTrendResponse> monthlyTrend(
            @RequestParam(name = "months_count", required = false, defaultValue = "6") int monthsCount) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(reportQueryService.monthlyTrend(userId, monthsCount));
    }
}
