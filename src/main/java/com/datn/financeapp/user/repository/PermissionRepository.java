package com.datn.financeapp.user.repository;

import com.datn.financeapp.user.entity.Permission;
import com.datn.financeapp.user.enums.PermissionName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByName(PermissionName name);
    boolean existsByName(PermissionName name);
}
