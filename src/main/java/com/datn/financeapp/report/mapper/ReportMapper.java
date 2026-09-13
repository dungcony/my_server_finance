package com.datn.financeapp.report.mapper;

import com.datn.financeapp.report.dto.response.ExportJobResponse;
import com.datn.financeapp.report.entity.ExportJob;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReportMapper {

    default ExportJobResponse toExportJobResponse(ExportJob job) {
        if (job == null) {
            return null;
        }
        String downloadUrl = "completed".equals(job.getStatus()) ? "/reports/export/" + job.getId() + "/download" : null;
        return new ExportJobResponse(
                job.getId(),
                job.getStatus(),
                downloadUrl,
                job.getExpiresAt(),
                job.getErrorMessage());
    }
}
