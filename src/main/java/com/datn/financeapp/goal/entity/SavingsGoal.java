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
 * Entity cho bảng {@code savings_goals} (db/migration/V4__so_no_muc_tieu.sql dòng 113-136).
 *
 * <p><b>Hai cột KHÔNG được backend ghi:</b> {@code saved_amount} và {@code status} do trigger
 * {@code trg_goal_contributions_sync} sở hữu — trigger tự {@code SUM()} lại toàn bộ
 * {@code goal_contributions} mỗi lần bảng con thay đổi, và tự chuyển trạng thái. Backend chỉ
 * chèn/xoá bản ghi {@code goal_contributions} rồi ĐỌC LẠI hai cột này.
 *
 * <p>Khác biệt so với trigger sổ nợ: {@code fn_goal_contributions_sync} KHÔNG có
 * {@code RAISE EXCEPTION} nào — mục tiêu tiết kiệm không chặn nạp vượt {@code target_amount}, chỉ
 * tự đánh dấu {@code completed}. Trigger cũng giữ nguyên {@code cancelled} (không kéo ngược về
 * {@code in_progress}), tương tự cách xử lý {@code written_off} ở sổ nợ.
 *
 * <p>{@code wallet_id} nullable + {@code ON DELETE SET NULL}: mục tiêu có thể không gắn ví nào,
 * khi đó chỉ ghi nhận tiến độ chứ không chuyển tiền thật được (GOAL_WALLET_REQUIRED).
 */
@Entity
@Table(name = "savings_goals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsGoal {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Ví dành riêng cho mục tiêu. Nullable — không có ví thì không nạp bằng giao dịch thật được. */
    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "target_amount", nullable = false)
    private Long targetAmount;

    /**
     * DO TRIGGER SỞ HỮU — backend không bao giờ gọi {@code setSavedAmount}. Đọc bằng
     * {@code SavingsGoalRepository.findSavedAmountNative} sau khi đổi bảng con.
     */
    @Column(name = "saved_amount", nullable = false)
    private Long savedAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "icon_id")
    private UUID iconId;

    /** DO TRIGGER SỞ HỮU — CHECK IN ('in_progress','completed','cancelled'). */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
