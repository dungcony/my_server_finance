package com.datn.financeapp.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi 202 của {@code POST /reports/export} ({@link #jobId}/{@link #status}) và của
 * {@code GET /reports/export/{job_id}} ({@link #downloadUrl}/{@link #expiresAt}/
 * {@link #errorMessage} thêm vào khi có). {@code @JsonInclude(NON_NULL)} để bỏ hẳn các trường
 * chưa có giá trị thay vì trả {@code null} tường minh, đúng mẫu {@code WalletResponse}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportJobResponse(
        UUID jobId, String status, String downloadUrl, Instant expiresAt, String errorMessage) {

    public static ExportJobResponse created(UUID jobId) {
        return new ExportJobResponse(jobId, "processing", null, null, null);
    }
}
