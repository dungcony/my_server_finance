package com.datn.financeapp.transaction.repository;

import com.datn.financeapp.transaction.entity.Transaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Transaction}. Khác {@code WalletRepository}, quyền D-27 ở đây suy
 * thẳng từ {@code transactions.user_id} — KHÔNG áp vế nhóm gia đình ({@code group_id}), vì đối
 * chiếu {@code db/migration/V2__giao_dich.sql} xác nhận bảng {@code transactions} không có cột
 * {@code group_id} (giống {@code CategoryRepository}, khác {@code WalletRepository}).
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Quyền D-27: transaction chỉ thuộc về đúng một user, không có nhánh nhóm. */
    Optional<Transaction> findByIdAndUserIdAndIsDeletedFalse(UUID id, UUID userId);

    /**
     * Dùng cho DELETE idempotent (CORE-06): tìm giao dịch theo id + quyền D-27 KHÔNG lọc
     * {@code is_deleted} — cho phép service phân biệt "không tồn tại/không có quyền" (404) với
     * "đã xoá mềm rồi, gọi lại vẫn 200" (không phải lỗi).
     */
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Dùng cho D-32 (plan sau): chặn xoá giao dịch đang gắn với một khoản trả nợ
     * ({@code debt_payments.transaction_id}, {@code ON DELETE RESTRICT} + {@code UNIQUE} —
     * db/migration/V4__so_no_muc_tieu.sql). Viết sẵn ở đây vì thuộc điểm nối của entity, dùng
     * lại thay vì viết lại điều kiện EXISTS ở nơi khác.
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM debt_payments WHERE transaction_id = :id)", nativeQuery = true)
    boolean existsDebtPaymentLink(@Param("id") UUID id);
}
