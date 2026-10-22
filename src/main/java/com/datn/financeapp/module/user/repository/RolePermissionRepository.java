package com.datn.financeapp.module.user.repository;

import com.datn.financeapp.module.user.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.RolePermissionId> {
    List<RolePermission> findByRoleId(UUID roleId);

    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
}
