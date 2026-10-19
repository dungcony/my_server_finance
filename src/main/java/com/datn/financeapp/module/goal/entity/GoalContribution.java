package com.datn.financeapp.module.goal.entity;

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
 * Thực thể đại diện cho một lần nạp / đóng góp tiền vào mục tiêu tiết kiệm (bảng {@code goal_contributions}).
 *
 * <p>Mỗi bản ghi ghi nhận một khoản tiền mà người dùng đã tích lũy thêm vào mục tiêu ({@link SavingsGoal}).
 *
 * <p><b>Quy tắc nghiệp vụ quan trọng:</b>
 * <ul>
 *   <li><b>Điểm duy nhất thay đổi tiến độ:</b> Để tăng hoặc giảm số tiền đã tiết kiệm của một mục tiêu,
 *       Backend chỉ cần thêm (insert) hoặc xóa (delete) bản ghi trong bảng này.
 *       Database Trigger ({@code trg_goal_contributions_sync}) sẽ tự động cập nhật tổng tiền
 *       và trạng thái tương ứng trên mục tiêu đó.
 *   </li>
 *   <li><b>Có hoặc không tạo giao dịch ví ({@code transactionId}):</b>
 *       <ul>
 *         <li><b>Có giao dịch (khác null):</b> Ghi nhận giao dịch trừ tiền ở ví nguồn ({@code transactionId})
 *             và giao dịch cộng tiền vào ví tiết kiệm ({@code targetTransactionId}).</li>
 *         <li><b>Không có giao dịch (null):</b> Người dùng chỉ ghi nhận số tiền đã tiết kiệm ở bên ngoài
 *             mà không phát sinh biến động số dư ví trong hệ thống.
 *         </li>
 *       </ul>
 *   </li>
 * </ul>
 */
@Entity
@Table(name = "goal_contributions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalContribution extends AssignedIdEntity {

    @Id
    private UUID id;

    @Override
    public UUID getId() {
        return id;
    }

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    /**
     * ID giao dịch trừ tiền ở ví nguồn (nếu có).
     * <p>Mang giá trị {@code null} khi người dùng chỉ ghi nhận tiến độ mà không trừ tiền ví thực tế.
     */
    @Column(name = "transaction_id")
    private UUID transactionId;

    /**
     * ID giao dịch cộng tiền vào ví tiết kiệm của mục tiêu (nếu có).
     */
    @Column(name = "target_transaction_id")
    private UUID targetTransactionId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "contributed_date", nullable = false)
    private LocalDate contributedDate;

    // createdAt, updatedAt, isDeleted thừa hưởng từ AssignedIdEntity
}
