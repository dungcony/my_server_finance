package com.datn.financeapp.budget.entity;

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
 * Entity cho bảng {@code budgets} (db/migration/V3__ngan_sach.sql).
 *
 * <p>CỐ Ý KHÔNG CÓ cột "đã chi": {@code spent_amount} tính tại chỗ mỗi lần đọc qua view
 * {@code v_budget_progress} (CLAUDE.md quy tắc bất biến §6). Một kỳ ngân sách chỉ vài chục
 * giao dịch nên tính trực tiếp luôn chính xác, không cần đồng bộ.
 *
 * <p>CŨNG KHÔNG CÓ cột {@code is_deleted} — đối chiếu schema V3 thật (CORE-11): xoá ngân sách là
 * tắt {@code is_active}, không xoá bản ghi và không thêm cột mới.
 *
 * <p>Ba lớp phòng thủ ở tầng CSDL mà service dựa vào thay vì tự SELECT kiểm tra trước:
 * {@code ex_bud_no_overlap} (EXCLUDE gist chặn ngân sách trùng), {@code ck_bud_owner} (cá nhân
 * HOẶC nhóm), và trigger {@code trg_budgets_validate} (chỉ danh mục chi).
 */
@Entity
@Table(name = "budgets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget {

    @Id
    private UUID id;

    /** Ngân sách cá nhân hoặc nhóm — đúng 1 trong 2 có giá trị (ck_bud_owner). */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** NULL = áp dụng cho mọi ví. */
    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "limit_amount", nullable = false)
    private Long limitAmount;

    /** CHECK IN ('week','month','quarter','year') — ck_bud_period. */
    @Column(name = "period_type", nullable = false)
    private String periodType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
