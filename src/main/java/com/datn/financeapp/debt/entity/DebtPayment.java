package com.datn.financeapp.debt.entity;

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
 * Entity cho bảng {@code debt_payments} (db/migration/V4__so_no_muc_tieu.sql) — mỗi lần trả nợ là
 * một bản ghi riêng, vì một khoản nợ trả được nhiều lần.
 *
 * <p>Đây là bảng DUY NHẤT backend được ghi khi muốn đổi tiến độ khoản nợ: chèn bản ghi để cộng
 * dồn, xoá bản ghi để hoàn tác. Trigger {@code trg_debt_payments_sync} lo phần cập nhật
 * {@code debts.paid_amount}/{@code debts.status} — kể cả việc MỞ LẠI {@code settled →
 * outstanding} khi huỷ một lần trả (D-46), vốn là bước dễ quên nhất.
 *
 * <p>Ràng buộc schema đáng nhớ: {@code transaction_id} có {@code ON DELETE RESTRICT} + UNIQUE
 * ({@code uq_dp_txn}) — mỗi giao dịch chỉ gắn với đúng một lần trả nợ, và không xoá CỨNG được
 * giao dịch còn bản ghi trả nợ trỏ tới (xoá MỀM thì không vướng).
 */
@Entity
@Table(name = "debt_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtPayment {

    @Id
    private UUID id;

    @Column(name = "debt_id", nullable = false)
    private UUID debtId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "paid_date", nullable = false)
    private LocalDate paidDate;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
