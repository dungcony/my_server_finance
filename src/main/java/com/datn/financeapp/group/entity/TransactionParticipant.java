package com.datn.financeapp.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Value Object đại diện cho một người tham gia phân bổ chi phí trong giao dịch chi tiêu nhóm (EXPENSE).
 * <p>
 * <b>Thiết kế kiến trúc DDD (Domain-Driven Design):</b>
 * <ul>
 *   <li>Được đánh dấu là {@link Embeddable} và thuộc quyền quản lý của Aggregate Root {@link GTransaction}.</li>
 *   <li>Không có định danh riêng (ID độc lập) và không chứa quan hệ ngược về cha (unidirectional),
 *       vòng đời hoàn toàn do {@code GroupTransaction} kiểm soát thông qua {@code @ElementCollection}.</li>
 *   <li>Tương ứng với bảng {@code group_transaction_participants} trong Database với khóa chính kép
 *       ({@code group_transaction_id, user_id}).</li>
 * </ul>
 * </p>
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionParticipant {

    /**
     * ID của người dùng tham gia chia khoản chi tiêu này.
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Số tiền phân bổ cụ thể cho người này (VNĐ).
     * <ul>
     *   <li>Nếu có giá trị: Người này chịu đúng số tiền được chỉ định (chia tùy chỉnh).</li>
     *   <li>Nếu {@code null}: Người này tham gia theo hình thức chia đều với các thành viên khác.</li>
     * </ul>
     */
    @Column(name = "share_amount")
    private Long shareAmount;
}
