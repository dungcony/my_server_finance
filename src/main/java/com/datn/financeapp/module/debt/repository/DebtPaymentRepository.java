package com.datn.financeapp.module.debt.repository;

import com.datn.financeapp.module.debt.entity.DebtPayment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository JPA cho {@link DebtPayment}. Mọi method đều lọc theo {@code debtId} và {@code is_deleted = false}.
 */
public interface DebtPaymentRepository extends JpaRepository<DebtPayment, UUID> {

    List<DebtPayment> findByDebtIdAndIsDeletedFalseOrderByPaidDateDesc(UUID debtId);

    Optional<DebtPayment> findByIdAndDebtIdAndIsDeletedFalse(UUID id, UUID debtId);

    List<DebtPayment> findByDebtIdAndIsDeletedFalse(UUID debtId);
}
