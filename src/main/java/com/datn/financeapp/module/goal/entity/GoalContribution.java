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
 * Thực thể đại diện cho một lần nạp / rút tiền khỏi mục tiêu tiết kiệm (bảng {@code goal_contributions}).
 *
 * <p>Mỗi bản ghi ghi nhận một khoản tiền nạp (amount dương) hoặc rút (amount âm) của người dùng vào mục tiêu ({@link SavingGoal}).
 *
 * <p><b>Quy tắc nghiệp vụ quan trọng:</b>
 * <ul>
 *   <li><b>Điểm duy nhất thay đổi tiến độ:</b> Để tăng hoặc giảm số tiền đã tiết kiệm của một mục tiêu,
 *       Backend chỉ cần thêm (insert), sửa (update) hoặc xóa (delete) bản ghi trong bảng này.
 *       Database Trigger ({@code trg_goal_contributions_sync}) sẽ tự động tính toán tổng số tiền
 *       và cập nhật trạng thái tương ứng trên mục tiêu đó.
 *   </li>
 *   <li><b>Số tiền (amount):</b> Dương khi nạp thêm tiền, âm khi rút bớt tiền.</li>
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

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "contributed_date", nullable = false)
    private LocalDate contributedDate;

    @Column(name = "note")
    private String note;

    // createdAt, updatedAt, isDeleted thừa hưởng từ AssignedIdEntity
}
