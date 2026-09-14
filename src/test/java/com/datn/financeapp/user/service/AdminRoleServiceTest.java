package com.datn.financeapp.user.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.user.dto.request.AddPermissionRoleRequest;
import com.datn.financeapp.user.entity.Permission;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.RolePermission;
import com.datn.financeapp.user.enums.PermissionName;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.repository.PermissionRepository;
import com.datn.financeapp.user.repository.RolePermissionRepository;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.service.impl.ManagerRoleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private ManagerRoleServiceImpl adminRoleService;

    private final UUID roleId = UUID.randomUUID();
    private final UUID permId = UUID.randomUUID();

    @Test
    @DisplayName("TC_ROLE_SVC_01: Role không tồn tại -> NOT_FOUND")
    void addPermissionToRole_RoleNotFound_ThrowsNotFound() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminRoleService.addPermissionToRole(roleId, PermissionName.USERS_READ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());

        verifyNoInteractions(permissionRepository, rolePermissionRepository);
    }

    @Test
    @DisplayName("TC_ROLE_SVC_02: Permission không tồn tại -> NOT_FOUND")
    void addPermissionToRole_PermissionNotFound_ThrowsNotFound() {
        Role role = Role.builder().id(roleId).name(RoleName.ROLE_ADMIN).build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(permissionRepository.findByName(PermissionName.USERS_READ)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminRoleService.addPermissionToRole(roleId, PermissionName.USERS_READ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());

        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC_ROLE_SVC_03: Gán permission thành công -> Lưu RolePermission")
    void addPermissionToRole_Success_SavesRolePermission() {
        Role role = Role.builder().id(roleId).name(RoleName.ROLE_ADMIN).build();
        Permission permission = Permission.builder().id(permId).name(PermissionName.USERS_READ).build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(permissionRepository.findByName(PermissionName.USERS_READ)).thenReturn(Optional.of(permission));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permId)).thenReturn(false);

        adminRoleService.addPermissionToRole(roleId, PermissionName.USERS_READ);

        verify(rolePermissionRepository).save(any(RolePermission.class));
    }

    @Test
    @DisplayName("TC_ROLE_SVC_04: Gán bằng tên role (RoleName) -> Thành công")
    void addPermissionToRole_ByRoleName_Success() {
        Role role = Role.builder().id(roleId).name(RoleName.ROLE_ADMIN).build();
        Permission permission = Permission.builder().id(permId).name(PermissionName.USERS_READ).build();

        when(roleRepository.findByName(RoleName.ROLE_ADMIN)).thenReturn(Optional.of(role));
        when(permissionRepository.findByName(PermissionName.USERS_READ)).thenReturn(Optional.of(permission));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permId)).thenReturn(false);

        adminRoleService.addPermissionToRole(new AddPermissionRoleRequest(RoleName.ROLE_ADMIN, PermissionName.USERS_READ));

        verify(rolePermissionRepository).save(any(RolePermission.class));
    }
}
