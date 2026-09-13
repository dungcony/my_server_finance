package com.datn.financeapp.goal.entity;

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
 * Entity cho bảng {@code goal_contributions} (V4 dòng 143-159) — mỗi lần bỏ tiền vào mục tiêu là
 * một bản ghi riêng.
 *
 * <p>Đây là bảng DUY NHẤT backend được ghi khi muốn đổi tiến độ mục tiêu: chèn bản ghi để cộng
 * dồn, xoá bản ghi để hoàn tác. Trigger {@code trg_goal_contributions_sync} lo phần cập nhật
 * {@code savings_goals.saved_amount}/{@code status} — kể cả việc MỞ LẠI {@code completed →
 * in_progress} khi rút lại một lần nạp, vốn là bước dễ quên nhất.
 *
 * <p><b>Khác biệt quan trọng so với {@code DebtPayment}:</b> {@code transaction_id} ở đây
 * <b>nullable</b> + {@code ON DELETE SET NULL}, trong khi {@code debt_payments.transaction_id} là
 * {@code NOT NULL} + {@code ON DELETE RESTRICT}. Lý do nghiệp vụ: chế độ nạp
 * {@code create_transaction = false} chỉ ghi nhận tiến độ mà không chuyển tiền thật (tiền nằm
 * ngoài ứng dụng) — khi đó không có giao dịch nào để trỏ tới.
 *
 * <p>Cũng vì {@code SET NULL} thay vì {@code RESTRICT} mà thứ tự huỷ một lần nạp ngược với sổ nợ:
 * xoá giao dịch TRƯỚC, xoá bản ghi này SAU — xem {@code GoalService.cancelContribution}.
 *
 * <p>Lưu ý bảng này KHÔNG có cột {@code note} (khác {@code debt_payments}) — ghi chú nếu có chỉ
 * nằm trên giao dịch sinh kèm.
 */
@Entity
@Table(name = "goal_contributions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalContribution {

    @Id
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    // NULL khi chỉ ghi nhận tiến độ mà không chuyển tiền thật — CỐ Ý không có nullable = false.
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "contributed_date", nullable = false)
    private LocalDate contributedDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
