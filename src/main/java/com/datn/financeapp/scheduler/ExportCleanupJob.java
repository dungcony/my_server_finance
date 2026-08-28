package com.datn.financeapp.scheduler;

import com.datn.financeapp.report.entity.ExportJob;
import com.datn.financeapp.report.repository.ExportJobRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dọn tệp/bản ghi {@code export_jobs} quá hạn hằng ngày (D-45). Chạy 4h30 sáng giờ Việt Nam.
 *
 * <p>Xoá FILE ĐĨA TRƯỚC rồi mới xoá bản ghi CSDL — tránh mất bản ghi mà file vẫn còn trên đĩa
 * (rác không nguy hiểm bằng bản ghi trỏ tới file không tồn tại). Mỗi job xử lý ĐỘC LẬP: một file
 * lỗi xoá không chặn file khác (T-04-20).
 *
 * <p>Việc xoá từng bản ghi được uỷ quyền cho {@link ExportCleanupWorker} — bean riêng, tránh
 * transaction self-invocation nếu gọi {@code @Transactional} method từ trong cùng class (bài học
 * {@code IdempotencyTransactionHelper}, Phase 1).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExportCleanupJob {

    private final ExportJobRepository exportJobRepository;
    private final ExportCleanupWorker worker;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Ho_Chi_Minh")
    public void run() {
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
        } catch (Exception e) {
            log.error("Lỗi khi chạy tác vụ dọn export_jobs quá hạn", e);
        }
    }

    /** Bean riêng chỉ để method {@code deleteOne} đi qua đúng Spring AOP proxy cho {@code @Transactional}. */
    @Component
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
    }
}
