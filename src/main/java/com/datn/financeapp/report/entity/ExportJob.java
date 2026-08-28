package com.datn.financeapp.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity cho bảng {@code export_jobs} (db/migration/V10__export_jobs.sql) — tác vụ xuất báo cáo
 * bất đồng bộ (REPORT-05, D-59). {@code filePath} là đường dẫn tệp trên đĩa máy chủ, KHÔNG BAO
 * GIỜ trả thẳng ra API — chỉ backend dùng để phục vụ endpoint download (D-43, T-04-17).
 */
@Entity
@Table(name = "export_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportJob {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** CHECK IN ('csv','pdf','excel') — Phase 4 chỉ hỗ trợ csv (D-44). */
    @Column(name = "format", nullable = false)
    private String format;

    /** CHECK IN ('processing','completed','failed'). */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "error_message")
    private String errorMessage;

    /** Chỉ set khi {@code status = completed}; job dọn hằng ngày (D-45, Plan 07) quét theo cột này. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
