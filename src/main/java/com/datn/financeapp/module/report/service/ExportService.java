package com.datn.financeapp.module.report.service;

import com.datn.financeapp.module.report.dto.request.ExportRequest;
import com.datn.financeapp.module.report.dto.response.ExportJobResponse;

import java.util.UUID;

import org.springframework.core.io.Resource;

/**
 * Interface định nghĩa dịch vụ xuất dữ liệu báo cáo (CSV).
 * Cung cấp các phương thức quản lý tiến trình xuất dữ liệu như {@link #createJob(UUID, ExportRequest)},
 * {@link #getStatus(UUID, UUID)} và {@link #download(UUID, UUID)}.
 */
public interface ExportService {

    /**
     * Khởi tạo một tiến trình xuất dữ liệu mới.
     *
     * @param userId ID người dùng yêu cầu
     * @param req    thông tin bộ lọc và tham số xuất dữ liệu
     * @return {@link ExportJobResponse} chứa thông tin và trạng thái tiến trình
     */
    ExportJobResponse createJob(UUID userId, ExportRequest req);

    /**
     * Lấy thông tin trạng thái hiện tại của tiến trình xuất dữ liệu.
     *
     * @param userId ID người dùng
     * @param jobId  ID tiến trình xuất dữ liệu
     * @return {@link ExportJobResponse} chứa thông tin tiến trình
     */
    ExportJobResponse getStatus(UUID userId, UUID jobId);

    /**
     * Tải tệp CSV báo cáo đã xuất xong.
     *
     * @param userId ID người dùng
     * @param jobId  ID tiến trình xuất dữ liệu
     * @return {@link Resource} dữ liệu tệp CSV
     */
    Resource download(UUID userId, UUID jobId);
}
