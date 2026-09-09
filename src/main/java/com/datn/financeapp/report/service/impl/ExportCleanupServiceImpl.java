package com.datn.financeapp.report.service.impl;

import com.datn.financeapp.report.service.ExportCleanupService;

import com.datn.financeapp.report.entity.ExportJob;
import com.datn.financeapp.report.repository.ExportJobRepository;
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
 * Dọn tệp/bản ghi {@code export_jobs} quá hạn (D-45), và cứu các tác vụ kẹt ở {@code processing}.
 *
 * <p><b>Vì sao nằm ở {@code report/} chứ không ở {@code scheduler/}:</b> đây là nghiệp vụ của
 * module báo cáo — nó biết {@code export_jobs} có ý nghĩa gì, file nằm ở đâu, thế nào là "kẹt".
 * {@code scheduler/} chỉ là vỏ mỏng quyết định <i>khi nào</i> chạy. Trước nhóm H, toàn bộ logic
 * này nằm trong {@code ExportCleanupJob} và phải import lậu {@code ExportJobRepository} của
 * {@code report/} (quy tắc 11 CLAUDE.md).
 *
 * <p>Xoá FILE ĐĨA TRƯỚC rồi mới xoá bản ghi CSDL — tránh mất bản ghi mà file vẫn còn trên đĩa
 * (rác không nguy hiểm bằng bản ghi trỏ tới file không tồn tại). Mỗi job xử lý ĐỘC LẬP: một file
 * lỗi xoá không chặn file khác (T-04-20).
 *
 * <p>Việc xoá từng bản ghi được uỷ quyền cho {@link ExportCleanupWorker} — bean riêng, tránh
 * transaction self-invocation nếu gọi {@code @Transactional} method từ trong cùng class (bài học
 * {@code IdempotencyTransactionHelper}, Phase 1).
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
     * Chạy trọn một lượt dọn. Không ném ngoại lệ ra ngoài — bên gọi là tác vụ nền, không có ai
     * để báo lỗi ngoài log.
     *
     * @return số bản ghi quá hạn đã xoá được
     */
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
