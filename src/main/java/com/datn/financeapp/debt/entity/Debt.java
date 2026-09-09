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
 * Entity cho bảng {@code debts} (db/migration/V4__so_no_muc_tieu.sql).
 *
 * <p><b>RANH GIỚI TRIGGER — điểm dễ sai nhất của module này.</b> Hai cột {@code paid_amount} và
 * {@code status} do trigger {@code trg_debt_payments_sync} SỞ HỮU: mỗi khi bảng
 * {@code debt_payments} thay đổi, trigger tự {@code SUM()} lại và tự chuyển
 * {@code outstanding ⇄ settled}. Backend chỉ chèn/xoá bản ghi {@code debt_payments}, KHÔNG BAO
 * GIỜ ghi hai cột này — ngoại lệ DUY NHẤT là write-off (api/08 mục 7), nơi trigger cố ý có nhánh
 * {@code WHEN status = 'written_off' THEN 'written_off'} giữ nguyên giá trị.
 *
 * <p>Muốn biết {@code paidAmount}/{@code status} sau khi chèn thì ĐỌC LẠI bản ghi bằng
 * {@code DebtRepository.findByIdNative} — instance đang giữ trong Hibernate identity map mang
 * giá trị CŨ trước khi trigger chạy.
 *
 * <p>CỐ Ý KHÔNG CÓ cột {@code is_deleted} (đối chiếu schema V4 thật, CLAUDE.md backend quy tắc 0):
 * xoá khoản nợ là xoá CỨNG (D-48). Nhu cầu "giữ lại để xem" đã có chức năng riêng là write-off.
 */
@Entity
@Table(name = "debts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Debt {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    // CHECK IN ('lending','borrowing') — lending = người khác nợ tôi, borrowing = tôi nợ người khác.
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "counterparty_name", nullable = false)
    private String counterpartyName;

    @Column(name = "principal_amount", nullable = false)
    private Long principalAmount;

    // DO TRIGGER SỞ HỮU — chỉ đọc, không bao giờ ghi từ backend.
    @Column(name = "paid_amount", nullable = false)
    private Long paidAmount;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "note")
    private String note;

    // DO TRIGGER SỞ HỮU — ngoại lệ duy nhất được ghi là write-off (api/08 mục 7).
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "origin_transaction_id", nullable = false)
    private UUID originTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
