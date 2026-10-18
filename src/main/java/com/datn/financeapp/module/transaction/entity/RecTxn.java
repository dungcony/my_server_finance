package com.datn.financeapp.module.transaction.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.transaction.enums.FrequencyType;
import com.datn.financeapp.module.transaction.enums.TxnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Quản lý thông tin thiết lập giao dịch định kỳ (tự động phát sinh thu/chi).
 */
@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecTxn extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "category_code")
    private String categoryCode;

    // loại giao dịch sẽ được sinh ra mỗi kỳ (EXPENSE, INCOME, LOAN)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TxnType type;

    // số tiền cần dùng
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "note")
    private String note;

    // thời gian bắt đầu
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    // NULL = lặp vô hạn.
    @Column(name = "end_at")
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private FrequencyType frequency;

    @Column(name = "repeat_interval", nullable = false)
    private Integer interval;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

}
