package com.datn.financeapp.report.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.report.dto.response.ExportJobResponse;
import com.datn.financeapp.report.dto.request.ExportRequest;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
            throw new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED, "Định dạng " + req.format() + " chưa hỗ trợ, chỉ hỗ trợ csv ở phiên bản này.");
        }

        UUID jobId = UUID.randomUUID();
        exportJobRepository.save(ExportJob.builder()
                .id(jobId)
                .userId(userId)
                .format("csv")
                .status("processing")
                .createdAt(Instant.now())
                .build());

        // KHÔNG gọi thẳng runExport ở đây: @Async đẩy công việc sang thread khác NGAY LẬP TỨC,
        // trong khi bản ghi export_jobs vừa save vẫn nằm trong transaction chưa commit. Thread
        // export có transaction riêng nên không thấy bản ghi đó — nếu nó chạy nhanh hơn (dữ liệu
        // ít, tệp nhỏ), markCompleted khớp 0 dòng và TRÔI QUA IM LẶNG, rồi transaction này mới
        // commit đè lại trạng thái processing. Kết quả: tệp CSV đã nằm trên đĩa nhưng CSDL nói
        // job vẫn đang chạy, người dùng poll mãi không bao giờ nhận được link.
        //
        // afterCommit đảm bảo bản ghi đã hiện diện trong CSDL trước khi thread export bắt đầu.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                exportAsyncRunner.runExport(jobId, userId, req);
            }
        });

        return ExportJobResponse.created(jobId);
    }

    @Transactional(readOnly = true)
    public ExportJobResponse getStatus(UUID userId, UUID jobId) {
        ExportJob job = exportJobRepository
                .findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tệp xuất."));

        String downloadUrl = "completed".equals(job.getStatus()) ? "/reports/export/" + jobId + "/download" : null;
        return new ExportJobResponse(jobId, job.getStatus(), downloadUrl, job.getExpiresAt(), job.getErrorMessage());
    }

    @Transactional(readOnly = true)
    public Resource download(UUID userId, UUID jobId) {
        ExportJob job = exportJobRepository
                .findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tệp xuất."));

        if (job.getExpiresAt() == null || job.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.EXPORT_LINK_EXPIRED);
        }

        // Đường dẫn từ CSDL, KHÔNG nối input người dùng (T-04-17) — tên tệp do ExportAsyncRunner
        // sinh từ jobId (UUID).
        return new FileSystemResource(new File(job.getFilePath()));
    }
}
