package com.datn.financeapp.group.entity;

import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MoneySource;
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
 * <b>Các loại giao dịch hỗ trợ ({@link GTransactionType}):</b>
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
public class GTransaction {

    /**
     * Khóa chính định danh duy nhất của giao dịch.
     */
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
     * ID của thành viên thực hiện / đối ứng giao dịch (người chi tiền, nộp quỹ, nhận hoàn tiền, kiểm kê).
     */
    @Column(name = "transactor_id", nullable = false)
    private UUID transactorId;

    /**
     * ID người tạo bản ghi giao dịch này trên hệ thống.
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

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
    private GTransactionType type;

    /**
     * Trạng thái kiểm duyệt:
     * <ul>
     *   <li>{@code PENDING}: Đang chờ Trưởng nhóm hoặc Thủ quỹ phê duyệt (quỹ chưa thay đổi).</li>
     *   <li>{@code CONFIRMED}: Đã duyệt thành công (tiền quỹ nhóm đã được cộng/trừ tương ứng).</li>
     *   <li>{@code REJECTED}: Bị từ chối duyệt (quỹ không thay đổi).</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GTransactionStatus status = GTransactionStatus.PENDING;

    /**
     * ID của Trưởng nhóm hoặc Thủ quỹ thực hiện duyệt giao dịch.
     */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /**
     * Thời điểm giao dịch được phê duyệt hoặc từ chối.
     */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /**
     * Số tiền phát sinh của giao dịch (VNĐ, luôn là số dương > 0).
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * Thời điểm thực tế phát sinh giao dịch chi tiêu/nộp quỹ (do người dùng khai báo).
     */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * Ghi chú hoặc mô tả chi tiết nội dung giao dịch.
     */
    @Column(name = "note")
    private String note;

    /**
     * Thời điểm bản ghi được tạo trong hệ thống.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Thời điểm bản ghi được cập nhật lần cuối.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
    private List<TransactionParticipant> participants = new ArrayList<>();

    /**
     * Factory method tạo Aggregate Root {@link GTransaction} mới cho EXPENSE hoặc CONTRIBUTION.
     * Đóng gói logic khởi tạo ID, timestamp và chuẩn hóa dữ liệu.
     */
    public static GTransaction newTransaction(
            UUID groupId,
            UUID operatorId,
            UUID transactorId,
            UUID categoryId,
            GTransactionType type,
            MoneySource moneySource,
            Long amount,
            Instant occurredAt,
            String note,
            GTransactionStatus status,
            UUID reviewedBy,
            Instant reviewedAt,
            List<TransactionParticipant> participants
    ) {
        Instant now = Instant.now();
        return GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(moneySource)
                .transactorId(transactorId)
                .createdBy(operatorId)
                .categoryId(categoryId)
                .type(type)
                .status(status)
                .reviewedBy(reviewedBy)
                .reviewedAt(reviewedAt)
                .amount(amount)
                .occurredAt(occurredAt)
                .note(note != null ? note.trim() : null)
                .participants(participants != null ? new ArrayList<>(participants) : new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Factory method tạo giao dịch trực tiếp từ quỹ nhóm (REFUND).
     * Các giao dịch này luôn tự động ở trạng thái CONFIRMED từ nguồn tiền FUND.
     */
    public static GTransaction newDirectTransaction(
            UUID groupId,
            UUID creatorId,
            UUID transactorId,
            GTransactionType type,
            long amount,
            Instant occurredAt,
            String note
    ) {
        Instant now = Instant.now();
        return GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.FUND)
                .transactorId(transactorId)
                .createdBy(creatorId)
                .categoryId(null)
                .type(type)
                .status(GTransactionStatus.CONFIRMED)
                .reviewedBy(creatorId)
                .reviewedAt(now)
                .amount(amount)
                .occurredAt(occurredAt)
                .note(note != null ? note.trim() : null)
                .participants(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
