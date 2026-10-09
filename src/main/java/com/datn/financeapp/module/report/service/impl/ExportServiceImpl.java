package com.datn.financeapp.module.report.service.impl;

import com.datn.financeapp.module.report.service.ExportService;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.report.dto.response.ExportJobResponse;
import com.datn.financeapp.module.report.dto.request.ExportRequest;
import com.datn.financeapp.module.report.entity.ExportJob;
import com.datn.financeapp.module.report.export.ExportAsyncRunner;
import com.datn.financeapp.module.report.mapper.ReportMapper;
import com.datn.financeapp.module.report.repository.ExportJobRepository;

import java.io.File;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Service xử lý các thao tác xuất báo cáo dữ liệu CSV bất đồng bộ.
 * Triển khai giao diện {@link ExportService}, tương tác với {@link ExportJobRepository} và {@link ExportAsyncRunner}.
 */
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final ExportJobRepository exportJobRepository;
    private final ExportAsyncRunner exportAsyncRunner;
    private final ReportMapper reportMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ExportJobResponse createJob(UUID userId, ExportRequest req) {
        if (!"csv".equals(req.format())) {
            throw new BusinessException(ErrorCode.EXPORT_FORMAT_NOT_SUPPORTED, "Định dạng " + req.format() + " chưa hỗ trợ, chỉ hỗ trợ csv ở phiên bản này.");
        }

        UUID jobId = UUID.randomUUID();
        exportJobRepository.save(ExportJob.builder()
                .id(jobId)
                .userId(userId)
                .format("csv")
                .status("processing")
                .createdAt(Instant.now())
                .build());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                exportAsyncRunner.runExport(jobId, userId, req);
            }
        });

        return ExportJobResponse.created(jobId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ExportJobResponse getStatus(UUID userId, UUID jobId) {
        ExportJob job = exportJobRepository
                .findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tệp xuất."));

        return reportMapper.toExportJobResponse(job);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Resource download(UUID userId, UUID jobId) {
        ExportJob job = exportJobRepository
                .findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tệp xuất."));

        if (job.getExpiresAt() == null || job.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.EXPORT_LINK_EXPIRED);
        }

        return new FileSystemResource(new File(job.getFilePath()));
    }
}
