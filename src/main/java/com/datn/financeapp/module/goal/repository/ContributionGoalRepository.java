package com.datn.financeapp.module.goal.repository;

import com.datn.financeapp.module.goal.entity.GoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA cho {@link GoalContribution}.
 */
public interface ContributionGoalRepository extends JpaRepository<GoalContribution, UUID>, JpaSpecificationExecutor<GoalContribution> {

    Optional<GoalContribution> findByIdAndGoalIdAndIsDeletedFalse(UUID id, UUID goalId);

    @Modifying
    @Query("""
            update GoalContribution gc
            set gc.isDeleted = true
            where gc.goalId = :goalId
            """)
    void removeForSavingGoal(@Param("goalId") UUID goalId);

}
