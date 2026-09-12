package com.datn.financeapp.user.repository;

import com.datn.financeapp.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRole.UserRoleId> {
    List<UserRole> findByUserId(UUID userId);
    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);
    void deleteByUserIdAndRoleId(UUID userId, UUID roleId);
}
