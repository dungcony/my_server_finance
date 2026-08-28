package com.datn.financeapp.report.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.report.dto.ExportJobResponse;
import com.datn.financeapp.report.dto.ExportRequest;
import com.datn.financeapp.report.entity.ExportJob;
import com.datn.financeapp.report.export.ExportAsyncRunner;
import com.datn.financeapp.report.repository.ExportJobRepository;
import java.io.File;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REPORT-05 (api/06-BAO-CAO.md mục 7, D-59) — xuất báo cáo CSV bất đồng bộ. {@code createJob}
 * chặn {@code pdf}/{@code excel} TRƯỚC KHI ghi bất kỳ bản ghi {@code export_jobs} nào (D-44):
 * check format nằm ở dòng ĐẦU method, không tạo rác dữ liệu cho định dạng chưa hỗ trợ.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ExportJobRepository exportJobRepository;
    private final ExportAsyncRunner exportAsyncRunner;

    @Transactional
    public ExportJobResponse createJob(UUID userId, ExportRequest req) {
        if (!"csv".equals(req.format())) {
            throw new BusinessException(
                    "FORMAT_NOT_SUPPORTED",
                    HttpStatus.NOT_IMPLEMENTED.value(),
                    "Định dạng " + req.format() + " chưa hỗ trợ, chỉ hỗ trợ csv ở phiên bản này.");
        }

        UUID jobId = UUID.randomUUID();
        exportJobRepository.save(ExportJob.builder()
                .id(jobId)
                .userId(userId)
                .format("csv")
                .status("processing")
                .createdAt(Instant.now())
                .build());

        exportAsyncRunner.runExport(jobId, userId, req);

        return ExportJobResponse.created(jobId);
    }

    @Transactional(readOnly = true)
    public ExportJobResponse getStatus(UUID userId, UUID jobId) {
        ExportJob job = exportJobRepository
                .findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy tệp xuất."));

        String downloadUrl = "completed".equals(job.getStatus()) ? "/reports/export/" + jobId + "/download" : null;
        return new ExportJobResponse(jobId, job.getStatus(), downloadUrl, job.getExpiresAt(), job.getErrorMessage());
    }

    @Transactional(readOnly = true)
    public Resource download(UUID userId, UUID jobId) {
        ExportJob job = exportJobRepository
                .findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy tệp xuất."));

        if (job.getExpiresAt() == null || job.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(
                    "EXPORT_LINK_EXPIRED", HttpStatus.GONE.value(), "Đường dẫn tải đã hết hạn.");
        }

        // Đường dẫn từ CSDL, KHÔNG nối input người dùng (T-04-17) — tên tệp do ExportAsyncRunner
        // sinh từ jobId (UUID).
        return new FileSystemResource(new File(job.getFilePath()));
    }
}
