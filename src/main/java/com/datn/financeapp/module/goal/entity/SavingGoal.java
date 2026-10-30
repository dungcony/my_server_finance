package com.datn.financeapp.module.goal.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.goal.enums.SavingGoalStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thực thể đại diện cho Mục tiêu tiết kiệm (bảng {@code savings_goals}).
 *
 * <p>
 * Mỗi bản ghi quản lý một mục tiêu tích lũy tài chính độc lập của người dùng
 * (ví dụ: Mua xe, Đi du lịch, Quỹ dự phòng...).
 *
 * <p>
 * <b>Quy tắc nghiệp vụ quan trọng:</b>
 * <ul>
 * <li><b>Tự động đồng bộ số tiền và trạng thái:</b> Hai trường
 * {@code savedAmount} và {@code status}
 * được quản lý hoàn toàn tự động bởi Database Trigger
 * ({@code trg_goal_contributions_sync}).
 * Backend không trực tiếp cập nhật giá trị hai trường này. Mỗi khi có bản ghi
 * đóng góp hoặc rút tiền
 * ({@link GoalContribution}) được thêm, sửa hoặc xóa, Trigger sẽ tự động tính
 * lại tổng tiền
 * và cập nhật trạng thái mục tiêu (chuyển sang {@code completed} khi đạt mục
 * tiêu hoặc quay lại {@code in_progress}).
 * </li>
 * <li><b>Nơi cất giữ tiền ({@code note}):</b>
 * Lưu trữ thông tin thực tế nơi người dùng cất giữ tiền tiết kiệm (ví dụ: "Nuôi
 * heo đất trong tủ",
 * "Gửi sổ tiết kiệm Agribank", "Gửi mẹ giữ hộ"...). Mục tiêu hoạt động độc lập,
 * không gắn với bất kỳ ví nào trong ứng dụng.
 * </li>
 * <li><b>Trạng thái mục tiêu ({@code status}):</b> Bao gồm:
 * <ul>
 * <li>{@code in_progress}: Đang trong quá trình tích lũy.</li>
 * <li>{@code completed}: Đã hoàn thành (tiền tích lũy >= mục tiêu).</li>
 * <li>{@code cancelled}: Đã bị hủy bởi người dùng (Trigger sẽ giữ nguyên trạng
 * thái này).</li>
 * </ul>
 * </li>
 * </ul>
 */
@Entity
@Table(name = "savings_goals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingGoal extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SavingGoalStatus status = SavingGoalStatus.IN_PROGRESS;

    @Column(name = "target_amount", nullable = false)
    private Long targetAmount;

    @Column(name = "saved_amount", nullable = false)
    @Builder.Default
    private Long savedAmount = 0L;

    @Column(name = "icon_code", length = 50)
    private String iconCode;

    @Column(name = "note")
    private String note;

    @Column(name = "target_date")
    private LocalDate targetDate;

    // createdAt, updatedAt, isDeleted thừa hưởng từ AssignedIdEntity
}
