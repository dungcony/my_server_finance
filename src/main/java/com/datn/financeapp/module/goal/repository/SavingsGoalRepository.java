package com.datn.financeapp.module.goal.repository;

import com.datn.financeapp.module.goal.entity.SavingGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA cho {@link SavingGoal}. Điều kiện quyền nằm NGAY TRONG SQL —
 * không "lấy hết rồi lọc ở Java". Không có quyền thì query trả rỗng để service ném 404.
 */
public interface SavingsGoalRepository extends JpaRepository<SavingGoal, UUID>, JpaSpecificationExecutor<SavingGoal> {

    Optional<SavingGoal> findByIdAndUserIdAndIsDeletedFalse(UUID id, UUID userId);

    Optional<SavingGoal> findByIdAndIsDeletedFalse(UUID id);
}
