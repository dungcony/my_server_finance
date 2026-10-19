package com.datn.financeapp.module.report.service;

import com.datn.financeapp.module.report.dto.request.ExportJobFilterRequest;
import com.datn.financeapp.module.report.dto.response.ExportJobResponse;
import com.datn.financeapp.module.report.dto.response.ReportSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service quản lý báo cáo và tiến trình xuất dữ liệu dành cho Quản trị viên (Admin).
 */
public interface ReportAdminService {

    /**
     * Tra cứu danh sách tiến trình xuất báo cáo toàn hệ thống có phân trang và bộ lọc.
     *
     * @param filter   bộ lọc tiến trình
     * @param pageable tham số phân trang
     * @return danh sách tiến trình phân trang
     */
    Page<ExportJobResponse> searchJobs(ExportJobFilterRequest filter, Pageable pageable);

    /**
     * Xem chi tiết một tiến trình xuất báo cáo bất kỳ.
     *
     * @param jobId ID tiến trình
     * @return chi tiết tiến trình
     */
    ExportJobResponse getJobDetail(UUID jobId);

    /**
     * Cưỡng chế xóa hoặc hủy tiến trình xuất báo cáo và tệp đính kèm.
     *
     * @param jobId ID tiến trình
     */
    void deleteJob(UUID jobId);

    /**
     * Xem tóm tắt báo cáo tài chính của người dùng bất kỳ.
     *
     * @param targetUserId ID người dùng mục tiêu
     * @param period       chu kỳ thống kê
     * @param fromDate     ngày bắt đầu
     * @param toDate       ngày kết thúc
     * @param walletId     ID ví (tùy chọn)
     * @return tóm tắt báo cáo tài chính
     */
    ReportSummaryResponse getUserSummary(
            UUID targetUserId, String period, LocalDate fromDate, LocalDate toDate, UUID walletId);
}
