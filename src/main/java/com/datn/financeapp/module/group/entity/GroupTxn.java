package com.datn.financeapp.module.group.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Aggregate Root đại diện cho một giao dịch tài chính trong nhóm quỹ chung.
 * <p>
 * <b>Các loại giao dịch hỗ trợ ({@link GroupTxnType}):</b>
 * <ul>
 *   <li>{@code EXPENSE}: Khoản chi tiêu chung của nhóm (lấy từ quỹ nhóm {@code FUND} hoặc cá nhân tự trả {@code PERSONAL}).</li>
 *   <li>{@code CONTRIBUTION}: Thành viên nộp tiền đóng góp vào quỹ nhóm.</li>
 *   <li>{@code REFUND}: Quỹ trả tiền cho thành viên (hoàn tiền túi hoặc trả lại tiền đã góp).</li>
 *   <li>{@code ADJUSTMENT_UP / ADJUSTMENT_DOWN}: Điều chỉnh số dư quỹ sau khi kiểm kê thực tế.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Quan hệ sở hữu danh sách người tham gia ({@code participants}):</b>
 * <ul>
 *   <li>Sử dụng {@link ElementCollection} kết hợp {@link CollectionTable}.</li>
 *   <li>Khi {@code GroupTransaction} được lưu hoặc xóa, Hibernate tự động cascade cập nhật bảng phụ
 *       {@code group_transaction_participants} mà không cần qua repository riêng.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "group_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupTxn extends AssignedIdEntity {

    @Id
    private UUID id;
    /**
     * ID của nhóm tài chính sở hữu giao dịch này.
     */
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    /**
     * Nguồn tiền thực hiện giao dịch (FUND: tiền quỹ nhóm, PERSONAL: tiền túi cá nhân).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "money_source", nullable = false, length = 20)
    private MoneySource moneySource;

    /**
     * ID của thành viên bỏ tiền túi (chi tiêu bằng tiền cá nhân, nộp quỹ). Null khi dùng tiền quỹ
     * (hoàn tiền, chi tiêu từ quỹ, kiểm kê); người nhận hoàn tiền nằm trong danh sách participants.
     */
    @Column(name = "transactor_id")
    private UUID transactorId;

    /**
     * ID người tạo bản ghi giao dịch này trên hệ thống.
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /**
     * ID của Trưởng nhóm hoặc Thủ quỹ thực hiện duyệt giao dịch.
     */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /**
     * ID danh mục chi tiêu (bắt buộc với EXPENSE, null đối với CONTRIBUTION/REFUND).
     */
    @Column(name = "category_id")
    private UUID categoryId;

    /**
     * Loại giao dịch nghiệp vụ (EXPENSE, CONTRIBUTION, REFUND, ADJUSTMENT...).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private GroupTxnType type;

    // Trạng thái kiểm duyệt:
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GroupTxnStatus status = GroupTxnStatus.PENDING;

    /**
     * Số tiền phát sinh của giao dịch (VNĐ, luôn là số dương > 0).
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * Ghi chú hoặc mô tả chi tiết nội dung giao dịch.
     */
    @Column(name = "note")
    private String note;

    /**
     * Thời điểm giao dịch được phê duyệt hoặc từ chối.
     */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    /**
     * Thời điểm thực tế phát sinh giao dịch chi tiêu/nộp quỹ (do người dùng khai báo).
     */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * Thời điểm xóa mềm (nếu null là giao dịch còn hiệu lực).
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Khóa lạc quan: mỗi lần ghi Hibernate tăng số này và chỉ ghi khi số trong DB còn khớp,
     * nhờ đó hai request cùng duyệt/sửa một giao dịch thì request đến sau bị từ chối
     * (quỹ không bị cộng trừ hai lần). Bản ghi mới để {@code null}.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Danh sách phân bổ chi phí cho các thành viên (chỉ dùng cho EXPENSE hoặc ADJUSTMENT).
     * <p>
     * Nếu danh sách này rỗng: Khoản chi được ngầm định chia đều cho toàn bộ thành viên đang ACTIVE
     * tại thời điểm phát sinh {@code occurredAt}.
     * </p>
     */
    @ElementCollection
    @CollectionTable(
            name = "group_transaction_participants",
            joinColumns = @JoinColumn(name = "group_transaction_id")
    )
    @Builder.Default
    private List<GroupTxnParticipant> participants = new ArrayList<>();
}
