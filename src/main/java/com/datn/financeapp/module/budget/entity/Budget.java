package com.datn.financeapp.module.budget.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.budget.enums.PeriodType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thực thể quản lý ngân sách chi tiêu của người dùng.
 * Cho phép người dùng đặt hạn mức chi tiêu cho một danh mục trong một khoảng thời gian (tuần, tháng, quý, năm).
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

    /** ID người dùng sở hữu ngân sách */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Mã danh mục chi tiêu (VD: FOOD, COFFEE, SHOPPING...) */
    @Column(name = "category_code", nullable = false)
    private String categoryCode;

    /** Số tiền thực tế đã chi tiêu trong kỳ ngân sách (VND) */
    @Column(name = "spent", nullable = false)
    private Long spent;

    /** Hạn mức chi tiêu tối đa đặt ra cho kỳ (VND) */
    @Column(name = "limit_amount", nullable = false)
    private Long limitAmount;

    /** Loại kỳ ngân sách: WEEK, MONTH, QUARTER, YEAR */
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private PeriodType periodType;

    /** Ngày bắt đầu kỳ tính toán theo lịch địa phương của người dùng */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Ngày kết thúc kỳ tính toán theo lịch địa phương của người dùng */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Cờ tự động gia hạn sang kỳ kế tiếp khi kỳ hiện tại kết thúc */
    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew;

    /** Trạng thái kích hoạt: true = đang theo dõi, false = tạm dừng */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
