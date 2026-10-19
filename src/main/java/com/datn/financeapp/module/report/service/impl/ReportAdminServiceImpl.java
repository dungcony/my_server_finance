package com.datn.financeapp.module.report.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.report.dto.request.ExportJobFilterRequest;
import com.datn.financeapp.module.report.dto.response.ExportJobResponse;
import com.datn.financeapp.module.report.dto.response.ReportSummaryResponse;
import com.datn.financeapp.module.report.entity.ExportJob;
import com.datn.financeapp.module.report.mapper.ReportMapper;
import com.datn.financeapp.module.report.repository.ExportJobRepository;
import com.datn.financeapp.module.report.service.ReportAdminService;
import com.datn.financeapp.module.report.service.ReportQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service triển khai các chức năng quản trị tiến trình xuất báo cáo và dữ liệu báo cáo hệ thống.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAdminServiceImpl implements ReportAdminService {

    private final ExportJobRepository exportJobRepository;
    private final ReportQueryService reportQueryService;
    private final ReportMapper reportMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ExportJobResponse> searchJobs(ExportJobFilterRequest filter, Pageable pageable) {
        UUID userId = filter != null ? filter.userId() : null;
        String status = filter != null && filter.status() != null && !filter.status().isBlank()
                ? filter.status().trim().toLowerCase() : null;
        String format = filter != null && filter.format() != null && !filter.format().isBlank()
                ? filter.format().trim().toLowerCase() : null;

        return exportJobRepository.searchByAdmin(userId, status, format, pageable)
                .map(reportMapper::toExportJobResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ExportJobResponse getJobDetail(UUID jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tiến trình xuất báo cáo."));
        return reportMapper.toExportJobResponse(job);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteJob(UUID jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tiến trình xuất báo cáo."));

        if (job.getFilePath() != null) {
            try {
                Files.deleteIfExists(Path.of(job.getFilePath()));
            } catch (Exception e) {
                log.warn("Không xóa được tệp vật lý {} của export job {}: {}", job.getFilePath(), jobId, e.getMessage());
            }
        }
        exportJobRepository.delete(job);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ReportSummaryResponse getUserSummary(
            UUID targetUserId, String period, LocalDate fromDate, LocalDate toDate, UUID walletId) {
        return reportQueryService.summary(targetUserId, period, fromDate, toDate, walletId);
    }
}
