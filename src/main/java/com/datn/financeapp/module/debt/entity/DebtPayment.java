package com.datn.financeapp.module.debt.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thực thể lưu lịch sử các đợt trả nợ (debt_payments).
 * Mỗi lần trả nợ là một bản ghi riêng, gắn với một giao dịch (transaction_id).
 * Trigger DB {@code trg_debt_payments_sync} tự động cộng dồn số tiền đã trả và cập nhật trạng thái khoản nợ cha.
 */
@Entity
@Table(name = "debt_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtPayment extends AssignedIdEntity {

    /** Khóa chính của đợt thanh toán */
    @Id
    private UUID id;

    /** ID khoản nợ cha */
    @Column(name = "debt_id", nullable = false)
    private UUID debtId;

    /** ID giao dịch phát sinh tương ứng với lần trả nợ */
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    /** Số tiền thanh toán đợt này (VND) */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** Ngày thanh toán */
    @Column(name = "paid_date", nullable = false)
    private LocalDate paidDate;

    /** Ghi chú cho đợt thanh toán */
    @Column(name = "note")
    private String note;

    // createdAt, updatedAt, isDeleted thừa hưởng từ AssignedIdEntity
}
