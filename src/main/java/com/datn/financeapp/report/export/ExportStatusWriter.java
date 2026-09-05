package com.datn.financeapp.report.export;

import com.datn.financeapp.report.repository.ExportJobRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi trạng thái cuối của một tác vụ xuất báo cáo trong TRANSACTION RIÊNG
 * ({@code REQUIRES_NEW}), tách hẳn khỏi transaction đọc dữ liệu/ghi tệp của
 * {@link ExportAsyncRunner}.
 *
 * <p><b>Vì sao phải tách bean:</b> trước đây {@code runExport} mang cả {@code @Async} lẫn
 * {@code @Transactional}, rồi {@code catch (Exception)} gọi thẳng {@code markFailed} bên trong
 * chính transaction vừa hỏng. Khi lỗi là lỗi CSDL (timeout, mất kết nối, sai kiểu), PostgreSQL
 * đánh dấu transaction là aborted và từ chối MỌI câu lệnh tiếp theo với
 * {@code current transaction is aborted} — nên {@code markFailed} cũng ném lỗi, không ghi được
 * gì. Bản ghi {@code export_jobs} kẹt vĩnh viễn ở {@code processing}: người dùng poll mãi không
 * nhận được link tải lẫn thông báo lỗi, và {@code ExportCleanupJob} không dọn được vì nó chỉ
 * quét {@code status = 'completed'}.
 *
 * <p>{@code REQUIRES_NEW} tạm dừng transaction đang chạy (nếu có) và mở một transaction mới hoàn
 * toàn độc lập, nên ghi được trạng thái lỗi kể cả khi transaction gốc đã hỏng. Tách sang bean
 * riêng là bắt buộc: gọi {@code this.markFailed()} trong cùng class là self-invocation, bỏ qua
 * proxy Spring và {@code REQUIRES_NEW} mất tác dụng hoàn toàn (cùng bài học với
 * {@code TransactionWriter} — D-31, và {@code DebtReminderWorker}).
 */
@Component
@RequiredArgsConstructor
public class ExportStatusWriter {

    private final ExportJobRepository exportJobRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID jobId, String filePath, Instant expiresAt) {
        exportJobRepository.markCompleted(jobId, filePath, expiresAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID jobId, String errorMessage) {
        exportJobRepository.markFailed(jobId, errorMessage);
    }
}
