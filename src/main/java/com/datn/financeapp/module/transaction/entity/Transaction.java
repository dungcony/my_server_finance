package com.datn.financeapp.module.transaction.entity;

import com.datn.financeapp.module.transaction.enums.TransactionSource;
import com.datn.financeapp.module.transaction.enums.TransactionType;
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
 *
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false)
    private Long amount;

    // ngày phát sinh giao dịch, KHÔNG có múi giờ, không convert.
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "note")
    private String note;

    @Column(name = "name")
    private String name;

    @Column(name = "source", nullable = false)
    private TransactionSource source;

    @Column(name = "recurring_id")
    private UUID recurringId;

    // link biên lai
    @Column(name = "receipt_url")
    private String receiptUrl;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
