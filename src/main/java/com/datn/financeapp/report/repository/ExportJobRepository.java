package com.datn.financeapp.report.repository;

import com.datn.financeapp.report.entity.ExportJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link ExportJob}. {@link #findByIdAndUserId} (không phải {@code findById}
 * trần) là điểm chặn IDOR duy nhất trước khi trả trạng thái/tải tệp (T-04-16), đúng mẫu
 * {@code TransactionRepository.findByIdAndUserId}.
 */
public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {

    @Query(value = "SELECT * FROM export_jobs WHERE id = :id AND user_id = :currentUser", nativeQuery = true)
    Optional<ExportJob> findByIdAndUserId(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Modifying
    @Query("UPDATE ExportJob e SET e.status = 'completed', e.filePath = :filePath, e.expiresAt = :expiresAt "
            + "WHERE e.id = :id")
    void markCompleted(
            @Param("id") UUID id, @Param("filePath") String filePath, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("UPDATE ExportJob e SET e.status = 'failed', e.errorMessage = :error WHERE e.id = :id")
    void markFailed(@Param("id") UUID id, @Param("error") String error);

    // Dùng cho job dọn quá hạn hằng ngày (D-45, Plan 07).
    @Query(value = "SELECT * FROM export_jobs WHERE status = 'completed' AND expires_at < :now", nativeQuery = true)
    List<ExportJob> findExpired(@Param("now") Instant now);

    /**
     * Các tác vụ kẹt ở {@code processing} quá lâu — lưới an toàn cuối cùng.
     *
     * <p>Đường đi bình thường luôn kết thúc ở {@code completed} hoặc {@code failed} nhờ
     * {@code ExportStatusWriter} (REQUIRES_NEW). Nhưng nếu tiến trình bị giết giữa chừng, hoặc
     * chính lượt ghi trạng thái lỗi cũng hỏng, bản ghi sẽ nằm lại {@code processing} vĩnh viễn:
     * người dùng poll mãi không nhận được câu trả lời nào, và {@code findExpired} không thấy nó
     * vì chỉ quét {@code completed}. Một tác vụ xuất CSV bình thường chạy trong vài giây, nên quá
     * một giờ chắc chắn là đã chết.
     */
    @Query(
            value = "SELECT * FROM export_jobs WHERE status = 'processing' AND created_at < :threshold",
            nativeQuery = true)
    List<ExportJob> findStuckProcessing(@Param("threshold") Instant threshold);
}
