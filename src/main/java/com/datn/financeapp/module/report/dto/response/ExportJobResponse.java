package com.datn.financeapp.module.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO cho thông tin và trạng thái của tiến trình xuất báo cáo dữ liệu.
 *
 * @param jobId        ID tiến trình xuất dữ liệu
 * @param status       trạng thái tiến trình (processing, completed, failed)
 * @param downloadUrl  đường dẫn tải tệp khi đã hoàn thành
 * @param expiresAt    thời điểm tệp hết hạn
 * @param errorMessage thông điệp lỗi nếu tiến trình thất bại
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportJobResponse(
        UUID jobId, String status, String downloadUrl, Instant expiresAt, String errorMessage) {

    /**
     * Khởi tạo DTO phản hồi khi tiến trình vừa được tạo thành công.
     *
     * @param jobId ID tiến trình
     * @return DTO {@link ExportJobResponse} với trạng thái processing
     */
    public static ExportJobResponse created(UUID jobId) {
        return new ExportJobResponse(jobId, "processing", null, null, null);
    }
}
