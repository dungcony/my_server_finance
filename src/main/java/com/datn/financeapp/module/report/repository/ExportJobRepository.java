package com.datn.financeapp.module.report.repository;

import com.datn.financeapp.module.report.entity.ExportJob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository thao tác cơ sở dữ liệu cho {@link ExportJob}.
 * Cung cấp các phương thức tìm kiếm và cập nhật trạng thái tiến trình xuất báo cáo như {@link #findByIdAndUserId(UUID, UUID)},
 * {@link #markCompleted(UUID, String, Instant)}, {@link #markFailed(UUID, String)}, {@link #findExpired(Instant)} và {@link #findStuckProcessing(Instant)}.
 */
public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {

    /**
     * Tìm kiếm tiến trình xuất báo cáo theo ID và thuộc về người dùng hiện tại.
     *
     * @param id          ID tiến trình
     * @param currentUser ID người dùng hiện tại
     * @return {@link Optional} chứa {@link ExportJob} nếu tìm thấy và thuộc về người dùng
     */
    @Query(value = "SELECT * FROM export_jobs WHERE id = :id AND user_id = :currentUser", nativeQuery = true)
    Optional<ExportJob> findByIdAndUserId(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    /**
     * Cập nhật trạng thái tiến trình thành hoàn thành (completed) kèm đường dẫn tệp và thời gian hết hạn.
     *
     * @param id        ID tiến trình
     * @param filePath  đường dẫn tệp trên hệ thống
     * @param expiresAt thời điểm tệp hết hạn
     */
    @Modifying
    @Query("""
            UPDATE ExportJob e SET e.status = 'completed', e.filePath = :filePath, e.expiresAt = :expiresAt
            WHERE e.id = :id
            """)
    void markCompleted(
            @Param("id") UUID id, @Param("filePath") String filePath, @Param("expiresAt") Instant expiresAt);

    /**
     * Cập nhật trạng thái tiến trình thành thất bại (failed) kèm thông điệp lỗi.
     *
     * @param id    ID tiến trình
     * @param error thông điệp lỗi
     */
    @Modifying
    @Query("UPDATE ExportJob e SET e.status = 'failed', e.errorMessage = :error WHERE e.id = :id")
    void markFailed(@Param("id") UUID id, @Param("error") String error);

    /**
     * Tìm danh sách các tiến trình xuất dữ liệu đã hoàn thành và quá hạn lưu trữ.
     *
     * @param now thời điểm hiện tại
     * @return danh sách các tiến trình {@link ExportJob} quá hạn
     */
    @Query(value = "SELECT * FROM export_jobs WHERE status = 'completed' AND expires_at < :now", nativeQuery = true)
    List<ExportJob> findExpired(@Param("now") Instant now);

    /**
     * Tìm danh sách các tiến trình bị kẹt ở trạng thái đang xử lý (processing) quá ngưỡng thời gian.
     *
     * @param threshold thời điểm mốc so sánh
     * @return danh sách các tiến trình {@link ExportJob} bị kẹt
     */
    @Query(
            value = "SELECT * FROM export_jobs WHERE status = 'processing' AND created_at < :threshold",
            nativeQuery = true)
    List<ExportJob> findStuckProcessing(@Param("threshold") Instant threshold);

    /**
     * Tìm kiếm và phân trang tiến trình xuất báo cáo toàn hệ thống dành cho Admin.
     */
    @Query("""
            SELECT e FROM ExportJob e
            WHERE (:userId IS NULL OR e.userId = :userId)
              AND (:status IS NULL OR e.status = :status)
              AND (:format IS NULL OR e.format = :format)
            ORDER BY e.createdAt DESC
            """)
    Page<ExportJob> searchByAdmin(
            @Param("userId") UUID userId,
            @Param("status") String status,
            @Param("format") String format,
            Pageable pageable);
}
