package com.datn.financeapp.report.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.report.dto.CategoryBreakdownResponse;
import com.datn.financeapp.report.dto.CategoryGroupBreakdownResponse;
import com.datn.financeapp.report.dto.DailyTrendResponse;
import com.datn.financeapp.report.dto.ExportJobResponse;
import com.datn.financeapp.report.dto.ExportRequest;
import com.datn.financeapp.report.dto.MonthlyTrendResponse;
import com.datn.financeapp.report.dto.ReportHomeResponse;
import com.datn.financeapp.report.dto.ReportSummaryResponse;
import com.datn.financeapp.report.service.ExportService;
import com.datn.financeapp.report.service.ReportQueryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 6 điểm cuối báo cáo chỉ đọc (REPORT-01..04, api/06-BAO-CAO.md mục 1-6) + xuất báo cáo CSV bất
 * đồng bộ (REPORT-05, mục 7, D-59).
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportQueryService reportQueryService;
    private final ExportService exportService;

    @GetMapping("/home")
    public ApiResponse<ReportHomeResponse> home(
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(name = "wallet_id", required = false) UUID walletId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(reportQueryService.home(userId, period, walletId));
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
            @RequestParam(required = false, defaultValue = "expense") String type,
            @RequestParam(name = "wallet_id", required = false) UUID walletId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(reportQueryService.byCategoryGroup(userId, period, fromDate, toDate, type, walletId));
    }

    @GetMapping("/by-category")
    public ApiResponse<CategoryBreakdownResponse> byCategory(
            @RequestParam(required = false) String period,
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "expense") String type,
            @RequestParam(required = false, defaultValue = "parent") String level,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestParam(name = "wallet_id", required = false) UUID walletId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(
                reportQueryService.byCategory(userId, period, fromDate, toDate, type, level, limit, walletId));
    }

    @GetMapping("/daily-trend")
    public ApiResponse<DailyTrendResponse> dailyTrend(
            @RequestParam(required = false) String month,
            @RequestParam(name = "wallet_id", required = false) UUID walletId) {
        UUID userId = SecurityContextUtil.currentUserId();
        YearMonth ym = month == null ? null : YearMonth.parse(month);
        return ApiResponse.of(reportQueryService.dailyTrend(userId, ym, walletId));
    }

    @GetMapping("/monthly-trend")
    public ApiResponse<MonthlyTrendResponse> monthlyTrend(
            @RequestParam(name = "months_count", required = false, defaultValue = "6") int monthsCount) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(reportQueryService.monthlyTrend(userId, monthsCount));
    }

    @PostMapping("/export")
    @Idempotent
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ExportJobResponse> export(@Valid @RequestBody ExportRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(exportService.createJob(userId, req));
    }

    @GetMapping("/export/{jobId}")
    public ApiResponse<ExportJobResponse> exportStatus(@PathVariable UUID jobId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(exportService.getStatus(userId, jobId));
    }

    @GetMapping("/export/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID jobId) {
        UUID userId = SecurityContextUtil.currentUserId();
        Resource resource = exportService.download(userId, jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + jobId + ".csv\"")
                .body(resource);
    }
}
