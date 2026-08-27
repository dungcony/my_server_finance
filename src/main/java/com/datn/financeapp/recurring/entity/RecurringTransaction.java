package com.datn.financeapp.recurring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity cho bảng {@code recurring_transactions} (db/migration/V2__giao_dich.sql dòng 49-83).
 *
 * <p><b>Không có {@code transfer}:</b> {@code ck_rec_type CHECK (type IN ('expense','income'))} —
 * khoản định kỳ chỉ sinh giao dịch thu hoặc chi, khác {@code transactions.type} vốn có ba giá trị.
 *
 * <p><b>Không có cột {@code is_deleted}:</b> bảng này không hỗ trợ xoá mềm, xem
 * {@code RecurringService.delete} để biết cách xử lý.
 *
 * <p>{@code next_run_date} là cột then chốt — tác vụ nền quét đúng cột này qua chỉ mục riêng
 * {@code idx_rec_due ON (next_run_date) WHERE is_enabled}.
 */
@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTransaction {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** CHECK IN ('expense', 'income') — KHÔNG có 'transfer'. */
    @Column(name = "type", nullable = false)
    private String type;

    /** Luôn dương (ck_rec_amount), kiểu Long theo CLAUDE.md §8 — không bao giờ Double. */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "note")
    private String note;

    /** CHECK IN ('day', 'week', 'month', 'year'). */
    @Column(name = "frequency", nullable = false)
    private String frequency;

    /**
     * Số chu kỳ mỗi lần nhảy, {@code >= 1}. Tên cột {@code interval} là TỪ KHOÁ của PostgreSQL nên
     * phải bọc dấu nháy kép trong {@code @Column}, nếu không Hibernate sinh SQL lỗi cú pháp.
     */
    @Column(name = "\"interval\"", nullable = false)
    private Integer interval;

    /**
     * Ngày bắt đầu GỐC — nguồn duy nhất của "ngày trong tháng" mong muốn khi tính kỳ kế tiếp. Không
     * bao giờ ghi đè bằng ngày đã làm tròn, xem
     * {@code RecurringDateCalculator}.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** NULL = lặp vô hạn. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
