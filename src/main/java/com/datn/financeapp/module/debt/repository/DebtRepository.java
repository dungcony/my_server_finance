package com.datn.financeapp.module.debt.repository;

import com.datn.financeapp.module.debt.entity.Debt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Debt}. Điều kiện quyền nằm NGAY TRONG SQL.
 * Lọc các bản ghi chưa bị xóa mềm (NOT is_deleted).
 */
public interface DebtRepository extends JpaRepository<Debt, UUID> {

    @Query(value = "SELECT * FROM debts WHERE id = :id AND user_id = :currentUser AND NOT is_deleted", nativeQuery = true)
    Optional<Debt> findByIdAndUserId(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Query(value = "SELECT * FROM debts WHERE id = :id AND NOT is_deleted", nativeQuery = true)
    Optional<Debt> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(
            value = """
                    SELECT * FROM debts WHERE user_id = :currentUser
                    AND NOT is_deleted
                    AND (CAST(:type AS text) IS NULL OR type = :type)
                    AND (CAST(:status AS text) IS NULL OR status = :status)
                    ORDER BY issued_date DESC, created_at DESC
                    """,
            nativeQuery = true)
    List<Debt> findAllForUser(
            @Param("currentUser") UUID currentUser, @Param("type") String type, @Param("status") String status);

    @Query(
            value = """
                    SELECT * FROM debts
                    WHERE NOT is_deleted
                    AND (CAST(:userId AS uuid) IS NULL OR user_id = :userId)
                    AND (CAST(:type AS text) IS NULL OR type = :type)
                    AND (CAST(:status AS text) IS NULL OR status = :status)
                    ORDER BY issued_date DESC, created_at DESC
                    """,
            nativeQuery = true)
    List<Debt> findAllForAdmin(
            @Param("userId") UUID userId, @Param("type") String type, @Param("status") String status);

    /**
     * Đọc lại tiến độ khoản nợ SAU khi trigger {@code trg_debt_payments_sync} đã UPDATE bảng cha.
     * Trả về scalar để tránh cache Hibernate identity map.
     */
    @Query(value = "SELECT paid_amount FROM debts WHERE id = :id AND NOT is_deleted", nativeQuery = true)
    Optional<Long> findPaidAmountNative(@Param("id") UUID id);

    @Query(value = "SELECT status FROM debts WHERE id = :id AND NOT is_deleted", nativeQuery = true)
    Optional<String> findStatusNative(@Param("id") UUID id);

    /**
     * Lấy các khoản nợ đang mở có hạn trả của user để kiểm tra đến hạn.
     */
    @Query(
            value = """
                    SELECT * FROM debts WHERE user_id = :currentUser AND status = 'outstanding'
                    AND due_date IS NOT NULL AND NOT is_deleted ORDER BY due_date
                    """,
            nativeQuery = true)
    List<Debt> findOutstandingWithDueDate(@Param("currentUser") UUID currentUser);

    /**
     * Quét toàn hệ thống các khoản nợ đang mở có hạn trả cho cron job nhắc nợ.
     */
    @Query(
            value = "SELECT * FROM debts WHERE status = 'outstanding' AND due_date IS NOT NULL AND NOT is_deleted",
            nativeQuery = true)
    List<Debt> findAllOutstandingWithDueDate();
}
