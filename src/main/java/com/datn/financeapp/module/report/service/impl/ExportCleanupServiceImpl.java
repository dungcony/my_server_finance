package com.datn.financeapp.module.report.service.impl;

import com.datn.financeapp.module.report.service.ExportCleanupService;

import com.datn.financeapp.module.report.entity.ExportJob;
import com.datn.financeapp.module.report.repository.ExportJobRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service thực thi việc dọn dẹp các tệp và bản ghi xuất báo cáo hết hạn hoặc bị kẹt.
 * Triển khai giao diện {@link ExportCleanupService}, tương tác với {@link ExportJobRepository}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportCleanupServiceImpl implements ExportCleanupService {

    // Xuất CSV bình thường xong trong vài giây; quá một giờ nghĩa là tiến trình đã chết.
    private static final long STUCK_THRESHOLD_HOURS = 1;

    private final ExportJobRepository exportJobRepository;
    private final ExportCleanupWorker worker;

    /**
     * {@inheritDoc}
     */
    @Override
    public int cleanUpExpiredAndStuck() {
        int deleted = deleteExpired();
        markStuckAsFailed();
        return deleted;
    }

    private int deleteExpired() {
        try {
            List<ExportJob> expired = exportJobRepository.findExpired(Instant.now());
            int deleted = 0;
            for (ExportJob job : expired) {
                try {
                    worker.deleteOne(job);
                    deleted++;
                } catch (Exception e) {
                    log.error("Lỗi khi dọn export job {}", job.getId(), e);
                }
            }
            log.info("Đã dọn {} bản ghi export_jobs quá hạn", deleted);
            return deleted;
        } catch (Exception e) {
            log.error("Lỗi khi chạy tác vụ dọn export_jobs quá hạn", e);
            return 0;
        }
    }

    // Tách riêng khỏi {@link #deleteExpired}: dọn quá hạn hỏng thì phần này vẫn phải chạy.
    private void markStuckAsFailed() {
        try {
            List<ExportJob> stuck = exportJobRepository.findStuckProcessing(
                    Instant.now().minus(STUCK_THRESHOLD_HOURS, ChronoUnit.HOURS));
            for (ExportJob job : stuck) {
                try {
                    worker.markStuckAsFailed(job.getId());
                } catch (Exception e) {
                    log.error("Lỗi khi đánh dấu export job kẹt {}", job.getId(), e);
                }
            }
            if (!stuck.isEmpty()) {
                log.warn("Đã đánh dấu thất bại {} tác vụ xuất báo cáo kẹt ở processing", stuck.size());
            }
        } catch (Exception e) {
            log.error("Lỗi khi quét export_jobs kẹt ở processing", e);
        }
    }

    // Bean riêng chỉ để hai method dưới đi qua đúng Spring AOP proxy cho {@code @Transactional}.
    @Service
    @RequiredArgsConstructor
    static class ExportCleanupWorker {

        private final ExportJobRepository exportJobRepository;

        @Transactional
        void deleteOne(ExportJob job) throws IOException {
            if (job.getFilePath() != null) {
                Files.deleteIfExists(Path.of(job.getFilePath()));
            }
            exportJobRepository.delete(job);
        }

        /**
         * Không xoá bản ghi kẹt mà chuyển sang {@code failed}: người dùng đang poll cần một câu
         * trả lời dứt khoát, còn xoá đi thì lượt poll kế tiếp nhận 404 — trông như tác vụ chưa
         * bao giờ tồn tại, khó hiểu hơn hẳn một thông báo thất bại rõ ràng.
         */
        @Transactional
        void markStuckAsFailed(UUID jobId) {
            exportJobRepository.markFailed(
                    jobId, "Tác vụ xuất báo cáo bị gián đoạn, vui lòng thử lại.");
        }
    }
}
