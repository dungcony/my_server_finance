package com.datn.financeapp.module.transaction.repository;

import com.datn.financeapp.module.transaction.entity.Transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    // : transaction chỉ thuộc về đúng một user, không có nhánh nhóm.
    Optional<Transaction> findByIdAndUserIdAndIsDeletedFalse(UUID id, UUID userId);

    /**
     * Dùng cho DELETE idempotent (CORE-06): tìm giao dịch theo id + quyền D-27 KHÔNG lọc
     * {@code is_deleted} — cho phép service phân biệt "không tồn tại/không có quyền" (404) với
     * "đã xoá mềm rồi, gọi lại vẫn 200" (không phải lỗi).
     */
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    Optional<Transaction> findByIdAndIsDeletedFalse(UUID transactionId);
}
