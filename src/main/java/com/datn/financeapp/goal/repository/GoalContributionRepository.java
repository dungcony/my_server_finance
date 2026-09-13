package com.datn.financeapp.goal.repository;

import com.datn.financeapp.goal.entity.GoalContribution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository JPA cho {@link GoalContribution}.
 *
 * <p>Không cần điều kiện quyền trong các query ở đây: mọi lối vào đều đi qua
 * {@code GoalService.loadOwnedGoal} kiểm tra quyền trên bảng cha trước, và {@code goalId} luôn là
 * tham số bắt buộc nên không thể với sang bản ghi của mục tiêu khác.
 */
public interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

    List<GoalContribution> findByGoalIdOrderByContributedDateDesc(UUID goalId);

    Optional<GoalContribution> findByIdAndGoalId(UUID id, UUID goalId);

    // Dùng khi xoá mục tiêu — phải lấy danh sách TRƯỚC khi ON DELETE CASCADE cuốn đi.
    List<GoalContribution> findByGoalId(UUID goalId);

    int countByGoalId(UUID goalId);
}
