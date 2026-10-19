package com.datn.financeapp.module.goal.repository;

import com.datn.financeapp.module.goal.entity.SavingsGoal;
import com.datn.financeapp.module.goal.helper.GoalSpecs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link SavingsGoal}. Điều kiện quyền nằm NGAY TRONG SQL —
 * không "lấy hết rồi lọc ở Java". Không có quyền thì query trả rỗng để service ném 404.
 */
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID>, JpaSpecificationExecutor<SavingsGoal> {

    @Query(value = "SELECT * FROM savings_goals WHERE id = :id AND user_id = :currentUser AND NOT is_deleted", nativeQuery = true)
    Optional<SavingsGoal> findByIdAndUserId(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Query(value = "SELECT * FROM savings_goals WHERE id = :id AND NOT is_deleted", nativeQuery = true)
    Optional<SavingsGoal> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    boolean existsByWalletIdAndIsDeletedFalseAndStatus(UUID walletId, String status);

    @Query(
            value = """
                    SELECT * FROM savings_goals WHERE user_id = :currentUser
                    AND NOT is_deleted
                    AND (CAST(:status AS text) IS NULL OR status = :status)
                    ORDER BY created_at DESC
                    """,
            nativeQuery = true)
    List<SavingsGoal> findAllForUser(@Param("currentUser") UUID currentUser, @Param("status") String status);

    default Page<SavingsGoal> findAllForAdmin(
            UUID userId,
            String status,
            String name,
            Pageable pageable) {
        return findAll(GoalSpecs.filter(userId, status, name), pageable);
    }

    /**
     * Đọc lại tiến độ mục tiêu SAU khi trigger {@code trg_goal_contributions_sync} đã UPDATE bảng cha.
     */
    @Query(value = "SELECT saved_amount FROM savings_goals WHERE id = :id AND NOT is_deleted", nativeQuery = true)
    Optional<Long> findSavedAmountNative(@Param("id") UUID id);

    @Query(value = "SELECT status FROM savings_goals WHERE id = :id AND NOT is_deleted", nativeQuery = true)
    Optional<String> findStatusNative(@Param("id") UUID id);
}
