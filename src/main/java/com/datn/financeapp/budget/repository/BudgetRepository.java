package com.datn.financeapp.budget.repository;

import com.datn.financeapp.budget.entity.Budget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Budget}. Điều kiện quyền D-27 nằm NGAY TRONG SQL, không "lấy hết rồi
 * lọc ở Java" — không có quyền thì trả rỗng để service ném 404 (không phải 403, T-04-05).
 *
 * <p><b>Phạm vi Phase 4:</b> chỉ có vế {@code user_id = :currentUser}. Vế {@code group_id}
 * (ngân sách chung của nhóm gia đình) thuộc Phase 5 — khi đó thêm điều kiện
 * {@code OR group_id IN (SELECT group_id FROM group_members WHERE user_id = :currentUser AND is_active)}
 * vào đúng những method này, không rải điều kiện ở nơi khác.
 */
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    @Query(value = "SELECT * FROM budgets b WHERE b.id = :id AND b.user_id = :currentUser", nativeQuery = true)
    Optional<Budget> findByIdForUser(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Query(
            value = "SELECT * FROM budgets b WHERE b.user_id = :currentUser "
                    + "AND (CAST(:isActive AS boolean) IS NULL OR b.is_active = :isActive) "
                    + "AND (CAST(:periodType AS text) IS NULL OR b.period_type = :periodType) "
                    + "ORDER BY b.start_date DESC",
            nativeQuery = true)
    List<Budget> findAllForUser(
            @Param("currentUser") UUID currentUser,
            @Param("isActive") Boolean isActive,
            @Param("periodType") String periodType);

    /**
     * BUDGET-05 — ba kỳ ngân sách gần nhất ĐÃ KẾT THÚC của cùng một danh mục, dùng làm cơ sở gợi
     * ý hạn mức. Không lọc {@code is_active} vì kỳ cũ đã bị JOB-02 tắt khi tự gia hạn.
     */
    @Query(
            value = "SELECT * FROM budgets b WHERE b.user_id = :currentUser "
                    + "AND b.category_id = :categoryId AND b.end_date < CURRENT_DATE "
                    + "ORDER BY b.end_date DESC LIMIT 3",
            nativeQuery = true)
    List<Budget> findLastThreeEndedPeriods(
            @Param("currentUser") UUID currentUser, @Param("categoryId") UUID categoryId);

    /**
     * Tổng chi thực tế của một khoảng ngày cho cây danh mục — dùng cho BUDGET-05 khi tính trung
     * bình 3 kỳ đã kết thúc. CỘNG GỘP DANH MỤC CON bắt buộc qua {@code fn_category_tree}
     * (CLAUDE.md §1) và loại {@code transfer} theo bản chất điều kiện {@code type = 'expense'}
     * (CLAUDE.md §2). Phạm vi quyền {@code t.user_id = :currentUser} ngay trong SQL.
     */
    @Query(
            value = "SELECT COALESCE(SUM(t.amount), 0) FROM transactions t "
                    + "WHERE t.type = 'expense' AND NOT t.is_deleted AND t.counts_in_report "
                    + "AND t.user_id = :currentUser "
                    + "AND t.date BETWEEN :startDate AND :endDate "
                    + "AND t.category_id IN (SELECT * FROM fn_category_tree(:categoryId)) "
                    + "AND (CAST(:walletId AS uuid) IS NULL OR t.wallet_id = :walletId)",
            nativeQuery = true)
    long sumExpenseInPeriod(
            @Param("currentUser") UUID currentUser,
            @Param("categoryId") UUID categoryId,
            @Param("walletId") UUID walletId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate);
}
