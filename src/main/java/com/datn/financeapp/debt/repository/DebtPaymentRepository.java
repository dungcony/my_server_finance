package com.datn.financeapp.debt.repository;

import com.datn.financeapp.debt.entity.DebtPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository JPA cho {@link DebtPayment}. Mọi method đều lọc theo {@code debtId} — service đã
 * kiểm tra quyền trên bản ghi {@code debts} cha trước đó, nên không cần lặp lại điều kiện
 * {@code user_id} ở đây (bảng {@code debt_payments} không có cột đó).
 *
 * <p>Chèn/xoá qua repository này là cách DUY NHẤT backend được đổi tiến độ khoản nợ — trigger
 * {@code trg_debt_payments_sync} tự lo {@code debts.paid_amount}/{@code debts.status}.
 */
public interface DebtPaymentRepository extends JpaRepository<DebtPayment, UUID> {

    List<DebtPayment> findByDebtIdOrderByPaidDateDesc(UUID debtId);

    Optional<DebtPayment> findByIdAndDebtId(UUID id, UUID debtId);

    /**
     * Dùng khi XOÁ khoản nợ (DEBT-06): phải lấy danh sách {@code transaction_id} TRƯỚC khi xoá
     * bản ghi {@code debts}, vì {@code ON DELETE CASCADE} sẽ cuốn theo cả {@code debt_payments} —
     * lấy sau thì không còn gì để hoàn tác số dư ví.
     */
    List<DebtPayment> findByDebtId(UUID debtId);
}
