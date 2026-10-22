package com.datn.financeapp.module.report.entity;

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
 * Entity đại diện cho bảng {@code export_jobs}.
 * Quản lý các tiến trình và kết quả xuất báo cáo dữ liệu dạng tệp (CSV).
 * Xem thêm các trường chính như {@link #getId()} và {@link #getStatus()}.
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

    // CHECK IN ('csv','pdf','excel') — Phase 4 chỉ hỗ trợ csv (D-44).
    @Column(name = "format", nullable = false)
    private String format;

    // CHECK IN ('processing','completed','failed').
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "error_message")
    private String errorMessage;

    // Chỉ set khi {@code status = completed}; job dọn hằng ngày (D-45, Plan 07) quét theo cột này.
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
