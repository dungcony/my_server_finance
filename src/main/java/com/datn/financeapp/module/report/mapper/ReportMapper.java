package com.datn.financeapp.module.report.mapper;

import com.datn.financeapp.module.report.dto.response.ExportJobResponse;
import com.datn.financeapp.module.report.entity.ExportJob;
import org.mapstruct.Mapper;

/**
 * Mapper chuyển đổi giữa entity {@link ExportJob} và các DTO response liên quan đến xuất báo cáo.
 * Cung cấp phương thức ánh xạ chính {@link #toExportJobResponse(ExportJob)}.
 */
@Mapper(componentModel = "spring")
public interface ReportMapper {

    /**
     * Chuyển đổi entity {@link ExportJob} sang DTO phản hồi {@link ExportJobResponse}.
     *
     * @param job entity tiến trình xuất báo cáo
     * @return DTO {@link ExportJobResponse} chứa thông tin trạng thái và URL tải về nếu có
     */
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
