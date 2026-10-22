package com.datn.financeapp.module.transaction.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.transaction.enums.TxnSource;
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
 *
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "category_code")
    private String categoryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TxnType type;

    @Column(name = "name")
    private String name;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "note")
    private String note;

    // thời gian phát sinh giao dịch, KHÔNG có múi giờ, không convert.
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
