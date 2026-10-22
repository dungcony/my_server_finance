package com.datn.financeapp.module.goal.repository;

import com.datn.financeapp.module.goal.entity.GoalContribution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository JPA cho {@link GoalContribution}.
 */
public interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

    List<GoalContribution> findByGoalIdAndIsDeletedFalseOrderByContributedDateDesc(UUID goalId);

    Optional<GoalContribution> findByIdAndGoalIdAndIsDeletedFalse(UUID id, UUID goalId);

    List<GoalContribution> findByGoalIdAndIsDeletedFalse(UUID goalId);

    int countByGoalIdAndIsDeletedFalse(UUID goalId);
}
