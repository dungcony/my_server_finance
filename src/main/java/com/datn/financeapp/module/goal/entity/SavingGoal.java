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
 * Thực thể đại diện cho Mục tiêu tiết kiệm (bảng {@code savings_goals}).
 *
 * <p>Mỗi bản ghi quản lý một mục tiêu tích lũy tài chính độc lập của người dùng (ví dụ: Mua xe, Đi du lịch, Quỹ dự phòng...).
 *
 * <p><b>Quy tắc nghiệp vụ quan trọng:</b>
 * <ul>
 *   <li><b>Tự động đồng bộ số tiền và trạng thái:</b> Hai trường {@code savedAmount} và {@code status}
 *       được quản lý hoàn toàn tự động bởi Database Trigger ({@code trg_goal_contributions_sync}).
 *       Backend không trực tiếp cập nhật giá trị hai trường này. Mỗi khi có bản ghi đóng góp hoặc rút tiền
 *       ({@link GoalContribution}) được thêm, sửa hoặc xóa, Trigger sẽ tự động tính lại tổng tiền
 *       và cập nhật trạng thái mục tiêu (chuyển sang {@code completed} khi đạt mục tiêu hoặc quay lại {@code in_progress}).
 *   </li>
 *   <li><b>Nơi cất giữ tiền ({@code note}):</b>
 *       Lưu trữ thông tin thực tế nơi người dùng cất giữ tiền tiết kiệm (ví dụ: "Nuôi heo đất trong tủ",
 *       "Gửi sổ tiết kiệm Agribank", "Gửi mẹ giữ hộ"...). Mục tiêu hoạt động độc lập, không gắn với bất kỳ ví nào trong ứng dụng.
 *   </li>
 *   <li><b>Trạng thái mục tiêu ({@code status}):</b> Bao gồm:
 *       <ul>
 *         <li>{@code in_progress}: Đang trong quá trình tích lũy.</li>
 *         <li>{@code completed}: Đã hoàn thành (tiền tích lũy >= mục tiêu).</li>
 *         <li>{@code cancelled}: Đã bị hủy bởi người dùng (Trigger sẽ giữ nguyên trạng thái này).</li>
 *       </ul>
 *   </li>
 * </ul>
 */
@Entity
@Table(name = "savings_goals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsGoal extends AssignedIdEntity {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Override
    public UUID getId() {
        return id;
    }

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "target_amount", nullable = false)
    private Long targetAmount;

    /**
     * Tổng số tiền đã tích lũy được tính đến thời điểm hiện tại.
     * <p><b>Lưu ý:</b> Giá trị này do Database Trigger tự động tính toán (tổng hợp từ bảng {@code goal_contributions}).
     * Backend không cập nhật trực tiếp trường này.
     */
    @Column(name = "saved_amount", nullable = false)
    private Long savedAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "icon_code", length = 50)
    private String iconCode;

    /**
     * Ghi chú nơi cất giữ tiền (ví dụ: "Nuôi heo đất trong tủ", "Gửi sổ tiết kiệm Agribank", "Gửi mẹ giữ hộ"...).
     */
    @Column(name = "note")
    private String note;

    /**
     * Trạng thái mục tiêu: {@code in_progress}, {@code completed}, hoặc {@code cancelled}.
     * <p><b>Lưu ý:</b> Việc chuyển đổi giữa {@code in_progress} và {@code completed} do Database Trigger đảm nhận.
     */
    @Column(name = "status", nullable = false)
    private String status;

    // createdAt, updatedAt, isDeleted thừa hưởng từ AssignedIdEntity
}
