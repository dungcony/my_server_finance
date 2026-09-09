package com.datn.financeapp.report.service;

import com.datn.financeapp.report.dto.request.ExportRequest;
import com.datn.financeapp.report.dto.response.ExportJobResponse;
import java.util.UUID;
import org.springframework.core.io.Resource;

// Public API của module Report cho các tác vụ xuất dữ liệu báo cáo.
public interface ExportService {

    ExportJobResponse createJob(UUID userId, ExportRequest req);

    ExportJobResponse getStatus(UUID userId, UUID jobId);

    Resource download(UUID userId, UUID jobId);
}
