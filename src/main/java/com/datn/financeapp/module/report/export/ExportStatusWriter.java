package com.datn.financeapp.module.report.export;

import com.datn.financeapp.module.report.repository.ExportJobRepository;

import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Component hạ tầng ghi trạng thái kết quả của tiến trình xuất báo cáo trong transaction độc lập (REQUIRES_NEW).
 * Cung cấp các phương thức cập nhật trạng thái như {@link #markCompleted(UUID, String, Instant)} và {@link #markFailed(UUID, String)}.
 */
@Component
@RequiredArgsConstructor
public class ExportStatusWriter {

    private final ExportJobRepository exportJobRepository;

    /**
     * Cập nhật trạng thái tiến trình thành hoàn thành.
     *
     * @param jobId     ID tiến trình
     * @param filePath  đường dẫn tệp xuất
     * @param expiresAt thời điểm tệp hết hạn
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID jobId, String filePath, Instant expiresAt) {
        exportJobRepository.markCompleted(jobId, filePath, expiresAt);
    }

    /**
     * Cập nhật trạng thái tiến trình thành thất bại.
     *
     * @param jobId        ID tiến trình
     * @param errorMessage thông điệp mô tả lỗi
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID jobId, String errorMessage) {
        exportJobRepository.markFailed(jobId, errorMessage);
    }
}
