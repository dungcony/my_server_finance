package com.datn.financeapp.module.debt.entity;

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
 * Thực thể quản lý các khoản nợ cá nhân (Sổ nợ - Cho vay & Đi vay).
 *
 * <p><b>Cơ chế hoạt động và luồng dữ liệu:</b>
 * <ul>
 *   <li><b>Tính độc lập:</b> Người vay/cho vay (counterparty) không cần có tài khoản trong app,
 *       chỉ cần lưu tên để người dùng theo dõi như một cuốn sổ tay cá nhân.</li>
 *   <li><b>Liên kết giao dịch:</b> Mỗi khi tạo khoản nợ, hệ thống tự động sinh một giao dịch
 *       (Thu hoặc Chi) và lưu ID vào {@code origin_transaction_id} để tiền trong ví luôn khớp.</li>
 *   <li><b>Trả nợ:</b> Các lần thanh toán nợ được lưu ở bảng {@code debt_payments},
 *       trigger DB sẽ tự động cộng dồn vào {@code paid_amount} và tự động chuyển trạng thái
 *       khi trả hết nợ.</li>
 * </ul>
 */
@Entity
@Table(name = "debts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Debt extends AssignedIdEntity {

    /**
     * Khóa chính của khoản nợ
     */
    @Id
    private UUID id;

    /**
     * ID người dùng sở hữu khoản nợ này
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Ví tiền liên kết lúc phát sinh khoản nợ (Ví nhận tiền vay hoặc ví rút tiền cho vay)
     */
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    /**
     * Loại nợ:
     * <ul>
     *   <li>{@code lending}: Cho vay (Người khác nợ tôi - Khoản phải thu)</li>
     *   <li>{@code borrowing}: Đi vay (Tôi nợ người khác - Khoản phải trả)</li>
     * </ul>
     */
    @Column(name = "type", nullable = false)
    private String type;

    /**
     * Tên của đối tác vay/cho vay (VD: Anh Nam, Bạn Linh, Ngân hàng ACB...)
     */
    @Column(name = "counterparty_name", nullable = false)
    private String counterpartyName;

    /**
     * Số tiền gốc ban đầu vay hoặc cho vay (VND)
     */
    @Column(name = "principal_amount", nullable = false)
    private Long principalAmount;

    /**
     * Số tiền đã thanh toán (VND).
     * Do Trigger DB tự động cộng dồn từ bảng debt_payments, backend không ghi đè trực tiếp trường này.
     */
    @Column(name = "paid_amount", nullable = false)
    private Long paidAmount;

    /**
     * Ngày phát sinh khoản nợ
     */
    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    /**
     * Ngày hẹn trả nợ (dùng để gửi thông báo nhắc nợ khi sắp đến hạn)
     */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /**
     * Ghi chú chi tiết về khoản nợ
     */
    @Column(name = "note")
    private String note;

    /**
     * Trạng thái khoản nợ:
     * <ul>
     *   <li>{@code outstanding}: Đang nợ (chưa thanh toán hoặc mới trả một phần)</li>
     *   <li>{@code settled}: Đã trả hết toàn bộ số nợ</li>
     *   <li>{@code written_off}: Xóa nợ / Không đòi được nữa</li>
     * </ul>
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * ID của giao dịch (Transaction) gốc được tạo ra đồng thời lúc lập khoản nợ này.
     * Dùng để kiểm toán và chặn việc xóa nhầm giao dịch làm lệch số sách.
     */
    @Column(name = "origin_transaction_id", nullable = false)
    private UUID originTransactionId;

    // Các trường createdAt, updatedAt, isDeleted đã được thừa hưởng sẵn từ AssignedIdEntity
}