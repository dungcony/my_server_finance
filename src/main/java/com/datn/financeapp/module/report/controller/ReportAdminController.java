package com.datn.financeapp.module.report.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.report.dto.request.ExportJobFilterRequest;
import com.datn.financeapp.module.report.dto.response.ExportJobResponse;
import com.datn.financeapp.module.report.dto.response.ReportSummaryResponse;
import com.datn.financeapp.module.report.service.ReportAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller quản trị báo cáo và tiến trình xuất dữ liệu toàn hệ thống.
 */
@RestController
@RequestMapping("/reports/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('reports:read') or hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
public class ReportAdminController {

    private final ReportAdminService reportAdminService;

    /**
     * Tra cứu tiến trình xuất báo cáo toàn hệ thống có phân trang và bộ lọc.
     *
     * @param userId   ID người dùng cần lọc (tùy chọn)
     * @param status   trạng thái tiến trình (tùy chọn)
     * @param format   định dạng tệp (tùy chọn)
     * @param pageable tham số phân trang
     * @return {@link ApiResponse} chứa trang danh sách {@link ExportJobResponse}
     */
    @GetMapping("/export-jobs")
    public ApiResponse<Page<ExportJobResponse>> searchJobs(
            @RequestParam(name = "user_id", required = false) UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String format,
            Pageable pageable) {
        ExportJobFilterRequest filter = new ExportJobFilterRequest(userId, status, format);
        return ApiResponse.of(reportAdminService.searchJobs(filter, pageable));
    }

    /**
     * Xem chi tiết một tiến trình xuất báo cáo bất kỳ.
     *
     * @param id ID tiến trình
     * @return {@link ApiResponse} chứa {@link ExportJobResponse}
     */
    @GetMapping("/export-jobs/{id}")
    public ApiResponse<ExportJobResponse> getJobDetail(@PathVariable UUID id) {
        return ApiResponse.of(reportAdminService.getJobDetail(id));
    }

    /**
     * Cưỡng chế hủy/xóa tiến trình xuất báo cáo.
     *
     * @param id ID tiến trình
     * @return {@link ApiResponse} rỗng khi hoàn tất
     */
    @DeleteMapping("/export-jobs/{id}")
    @PreAuthorize("hasAuthority('reports:delete') or hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public ApiResponse<Void> deleteJob(@PathVariable UUID id) {
        reportAdminService.deleteJob(id);
        return ApiResponse.of(null);
    }

    /**
     * Xem báo cáo tóm tắt tình hình thu chi của bất kỳ người dùng nào trong hệ thống.
     *
     * @param userId   ID người dùng mục tiêu
     * @param period   chu kỳ thống kê
     * @param fromDate ngày bắt đầu (nếu lọc theo khoảng thời gian tùy chỉnh)
     * @param toDate   ngày kết thúc (nếu lọc theo khoảng thời gian tùy chỉnh)
     * @param walletId ID ví (tùy chọn)
     * @return {@link ApiResponse} chứa {@link ReportSummaryResponse}
     */
    @GetMapping("/summary")
    public ApiResponse<ReportSummaryResponse> getUserSummary(
            @RequestParam(name = "user_id") UUID userId,
            @RequestParam(required = false) String period,
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(name = "wallet_id", required = false) UUID walletId) {
        return ApiResponse.of(reportAdminService.getUserSummary(userId, period, fromDate, toDate, walletId));
    }
}
