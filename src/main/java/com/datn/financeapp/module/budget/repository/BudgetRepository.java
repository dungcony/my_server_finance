package com.datn.financeapp.module.budget.repository;

import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.helper.BudgetSpent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA cho {@link Budget}.
 */
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByIdAndUserIdAndIsDeletedFalse(UUID id, UUID userId);

    Optional<Budget> findByIdAndIsDeletedFalse(UUID id);

    default Optional<Budget> findByIdAndUserId(UUID id, UUID userId) {
        return findByIdAndUserIdAndIsDeletedFalse(id, userId);
    }

    @Query("""
            SELECT b FROM Budget b
            WHERE b.userId = :userId
              AND b.isDeleted = FALSE
              AND (:isActive IS NULL OR b.isActive = :isActive)
              AND (:periodType IS NULL OR b.periodType = :periodType)
            ORDER BY b.startDate DESC
            """)
    List<Budget> search(
            @Param("userId") UUID userId,
            @Param("isActive") Boolean isActive,
            @Param("periodType") PeriodType periodType);

    /**
     * Tìm kiếm và phân trang ngân sách toàn hệ thống dành cho Admin.
     */
    @Query("""
            SELECT b FROM Budget b
            WHERE (:userId IS NULL OR b.userId = :userId)
              AND (:isDeleted IS NULL OR b.isDeleted = :isDeleted)
              AND (:isActive IS NULL OR b.isActive = :isActive)
              AND (:periodType IS NULL OR b.periodType = :periodType)
            ORDER BY b.createdAt DESC
            """)
    Page<Budget> searchByAdmin(
            @Param("userId") UUID userId,
            @Param("isDeleted") Boolean isDeleted,
            @Param("isActive") Boolean isActive,
            @Param("periodType") PeriodType periodType,
            Pageable pageable);

    @Query("""
            SELECT b FROM Budget b
            WHERE b.userId = :userId
              AND b.isDeleted = FALSE
              AND b.isActive = TRUE
              AND :date >= b.startDate AND :date <= b.endDate
            """)
    List<Budget> findActiveOn(@Param("userId") UUID userId, @Param("date") LocalDate date);

    /**
     * Các ngân sách đang hiệu lực mà ngày giao dịch nằm trong kỳ và danh mục thuộc
     * {@code categoryCodes} (danh mục của giao dịch cùng mọi tổ tiên).
     */
    @Query("""
            SELECT new com.datn.financeapp.module.budget.helper.BudgetSpent(\n                b.id, b.categoryCode, b.limitAmount, b.spent, b.endDate)\n            FROM Budget b\n            WHERE b.userId = :userId\n              AND b.isDeleted = FALSE\n              AND b.isActive = TRUE\n              AND :date >= b.startDate AND :date <= b.endDate\n              AND b.categoryCode IN :categoryCodes\n            """)
    List<BudgetSpent> findActiveByCategoryCodes(
            @Param("userId") UUID userId,
            @Param("categoryCodes") Collection<String> categoryCodes,
            @Param("date") LocalDate date);

    @Query("""
            SELECT new com.datn.financeapp.module.budget.helper.BudgetSpent(\n                b.id, b.categoryCode, b.limitAmount, b.spent, b.endDate)\n            FROM Budget b\n            WHERE b.id IN :ids AND b.isDeleted = FALSE\n            """)
    List<BudgetSpent> findSpentByIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT (COUNT(b) > 0) FROM Budget b
            WHERE b.userId = :userId
              AND b.categoryCode = :categoryCode
              AND b.isDeleted = FALSE
              AND b.isActive = TRUE
              AND b.startDate <= :endDate AND b.endDate >= :startDate
            """)
    boolean existsOverlapping(
            @Param("userId") UUID userId,
            @Param("categoryCode") String categoryCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT (COUNT(b) > 0) FROM Budget b
            WHERE b.userId = :userId
              AND b.categoryCode = :categoryCode
              AND b.isDeleted = FALSE
              AND b.isActive = TRUE
              AND b.startDate <= :endDate AND b.endDate >= :startDate
              AND b.id <> :exceptId
            """)
    boolean existsOverlapExceptId(
            @Param("userId") UUID userId,
            @Param("categoryCode") String categoryCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("exceptId") UUID exceptId);

    @Query("""
            SELECT b.id FROM Budget b
            WHERE b.isDeleted = FALSE
              AND b.isActive = TRUE
              AND b.autoRenew = TRUE
              AND b.endDate < :today
            """)
    List<UUID> findExpiredAutoRenewIds(@Param("today") LocalDate today);

    /**
     * Cộng {@code delta} (âm = hoàn tác) vào {@code spent} bằng một câu lệnh nguyên tử.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Budget b
            SET b.spent = CASE WHEN b.spent + :delta < 0 THEN 0 ELSE b.spent + :delta END
            WHERE b.id IN :ids
            """)
    int addSpent(@Param("ids") Collection<UUID> ids, @Param("delta") long delta);

    /**
     * Tổng chi đã ghi trong kỳ, tính thẳng từ bảng {@code transactions} bằng SQL native.
     * Sử dụng mốc Instant startInstant và endInstant tương ứng với 00:00:00 và 23:59:59 của ngày theo múi giờ user.
     */
    @Query(value = """
            SELECT CAST(COALESCE(SUM(t.amount), 0) AS BIGINT)
            FROM transactions t
            WHERE t.user_id = :userId
              AND t.type = 'EXPENSE'
              AND NOT t.is_deleted
              AND t.category_code IN (:categoryCodes)
              AND t.occurred_at >= :startInstant
              AND t.occurred_at <= :endInstant
            """, nativeQuery = true)
    long sumExpense(
            @Param("userId") UUID userId,
            @Param("categoryCodes") Collection<String> categoryCodes,
            @Param("startInstant") Instant startInstant,
            @Param("endInstant") Instant endInstant);
}
