package com.datn.financeapp.module.budget.repository;

import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.helper.BudgetSpent;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Budget}. Mọi truy vấn đọc ngân sách của người dùng đều kèm điều kiện
 * {@code userId} ngay trong câu lệnh (quy tắc nghiệp vụ 7): ngân sách của người khác không bao giờ được nạp lên.
 */
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT b FROM Budget b
            WHERE b.userId = :userId
              AND (:isActive IS NULL OR b.isActive = :isActive)
              AND (:periodType IS NULL OR b.periodType = :periodType)
            ORDER BY b.startDate DESC
            """)
    List<Budget> search(
            @Param("userId") UUID userId,
            @Param("isActive") Boolean isActive,
            @Param("periodType") PeriodType periodType);

    @Query("""
            SELECT b FROM Budget b
            WHERE b.userId = :userId AND b.isActive = TRUE
              AND :today BETWEEN b.startDate AND b.endDate
            """)
    List<Budget> findActiveOn(@Param("userId") UUID userId, @Param("today") LocalDate today);

    /**
     * Các ngân sách đang hiệu lực mà {@code date} nằm trong kỳ và danh mục của nó thuộc
     * {@code categoryIds} (danh mục của giao dịch cùng mọi tổ tiên — cộng gộp danh mục con vào cha).
     */
    @Query("""
            SELECT new com.datn.financeapp.module.budget.helper.BudgetSpent(
                b.id, b.categoryId, b.limitAmount, b.spent, b.endDate)
            FROM Budget b
            WHERE b.userId = :userId AND b.isActive = TRUE
              AND :date BETWEEN b.startDate AND b.endDate
              AND b.categoryId IN :categoryIds
            """)
    List<BudgetSpent> findActiveByCategoryIds(
            @Param("userId") UUID userId,
            @Param("categoryIds") Collection<UUID> categoryIds,
            @Param("date") LocalDate date);

    @Query("""
            SELECT new com.datn.financeapp.module.budget.helper.BudgetSpent(
                b.id, b.categoryId, b.limitAmount, b.spent, b.endDate)
            FROM Budget b WHERE b.id IN :ids
            """)
    List<BudgetSpent> findSpentByIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT (COUNT(b) > 0) FROM Budget b
            WHERE b.userId = :userId AND b.categoryId = :categoryId AND b.isActive = TRUE
              AND b.startDate <= :end AND b.endDate >= :start
            """)
    boolean existsOverlapping(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("""
            SELECT (COUNT(b) > 0) FROM Budget b
            WHERE b.userId = :userId AND b.categoryId = :categoryId AND b.isActive = TRUE
              AND b.startDate <= :end AND b.endDate >= :start AND b.id <> :exceptId
            """)
    boolean existsOverlapExceptId(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("exceptId") UUID exceptId);

    @Query("""
            SELECT b.id FROM Budget b
            WHERE b.isActive = TRUE AND b.autoRenew = TRUE AND b.endDate < :today
            """)
    List<UUID> findExpiredAutoRenewIds(@Param("today") LocalDate today);

    /**
     * Cộng {@code delta} (âm = hoàn tác) vào {@code spent} bằng một câu lệnh nguyên tử, không đọc-sửa-ghi
     * nên bulk nhiều giao dịch không dẫm lên nhau. Không đi dưới 0. Không xoá persistence context vì
     * người gọi đang giữ các entity giao dịch trong cùng transaction. Dấu của {@code delta} chỉ xuất hiện
     * ở đây: người gọi bên ngoài dùng {@code BudgetSpendService.addExpense/removeExpense}.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Budget b
            SET b.spent = CASE WHEN b.spent + :delta < 0 THEN 0 ELSE b.spent + :delta END
            WHERE b.id IN :ids
            """)
    int addSpent(@Param("ids") Collection<UUID> ids, @Param("delta") long delta);

    /**
     * Tổng chi đã ghi trong kỳ, tính thẳng từ bảng {@code transactions} để dựng {@code spent} ban đầu của
     * ngân sách tạo giữa kỳ. Đọc bảng bằng SQL chứ không gọi service {@code transaction} để budget không
     * phụ thuộc ngược module này (transaction đã phụ thuộc budget).
     */
    @Query(value = """
            SELECT CAST(COALESCE(SUM(t.amount), 0) AS BIGINT)
            FROM transactions t
            WHERE t.user_id = :userId AND t.type = 'expense'
              AND NOT t.is_deleted AND t.counts_in_report
              AND t.category_id IN (:categoryIds)
              AND t.date BETWEEN :start AND :end
            """, nativeQuery = true)
    long sumExpense(
            @Param("userId") UUID userId,
            @Param("categoryIds") Collection<UUID> categoryIds,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
