package com.datn.financeapp.module.transaction.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.transaction.enums.FreequenType;
import com.datn.financeapp.module.transaction.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Quản lý thông tin thiết lập giao dịch định kỳ (tự động phát sinh thu/chi).
 */
@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTransaction extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    // loại giao dịch sẽ được sinh ra mỗi kỳ (EXPENSE, INCOME, LOAN)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    // số tiền cần dùng
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "note")
    private String note;

    // khoảng thời gian
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private FreequenType frequency;

    // số lần freeq để nhảy ví dụ freeq là week interval là 3 thì là 3 tuần ghi 1 lần
    @Column(name = "\"interval\"", nullable = false)
    private Integer interval;

    // thời gian bắt đầu
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // NULL = lặp vô hạn.
    @Column(name = "end_date")
    private LocalDate endDate;

    // thời gian lần chạy tiếp theo
    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
