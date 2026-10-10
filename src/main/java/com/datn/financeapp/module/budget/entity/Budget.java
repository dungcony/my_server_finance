package com.datn.financeapp.module.budget.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.budget.enums.PeriodType;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bảng lưu hạn mức chi theo danh mục trong 1 kỳ
 * ví dụ: ăn uống tối đa 3tr
 */
@Entity
@Table(name = "budgets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    // đã tiêu
    @Column(name = "spent", nullable = false)
    private Long spent;

    // giới hạn chi tiêu vượt quá sẽ nhắc nhở
    @Column(name = "limit_amount", nullable = false)
    private Long limitAmount;

    // kiểu giai đoạn
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private PeriodType periodType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // tự động tạo
    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
