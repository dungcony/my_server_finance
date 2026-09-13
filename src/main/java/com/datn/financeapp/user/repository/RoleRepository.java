package com.datn.financeapp.user.repository;

import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(RoleName name);

    boolean existsByName(RoleName name);

    @NonNull
    @Override
    @Query("""
            select DISTINCT r
            from Role r
            left join fetch r.rolePermissions rp
            left join fetch rp.permission p
            """)
    List<Role> findAll();
}
