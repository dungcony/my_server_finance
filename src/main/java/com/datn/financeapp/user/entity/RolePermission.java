package com.datn.financeapp.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Entity bảng trung gian {@code role_permissions} theo ERD thiết kế phân quyền:
 * Role (1) ---- (0..*) RolePermission (*) ---- (1) Permission
 */
@Entity
@Table(name = "role_permissions")
@IdClass(RolePermission.RolePermissionId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    @Id
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Id
    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "permission_id", insertable = false, updatable = false)
    private Permission permission;

    public RolePermission(UUID roleId, UUID permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class RolePermissionId implements Serializable {
        private UUID roleId;
        private UUID permissionId;
    }
}
