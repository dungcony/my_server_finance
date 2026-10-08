package com.datn.financeapp.user.helper;

import com.datn.financeapp.user.dto.response.PermissionResponse;
import com.datn.financeapp.user.dto.response.RoleResponse;
import com.datn.financeapp.user.entity.Permission;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.enums.PermissionName;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.mapper.RoleMapper;
import com.datn.financeapp.user.repository.PermissionRepository;
import com.datn.financeapp.user.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolePermissionCacheHelperTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleMapper roleMapper;

    private RolePermissionCacheHelper cacheHelper;

    private final UUID roleId = UUID.randomUUID();
    private final UUID permId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cacheHelper = new RolePermissionCacheHelper(roleRepository, permissionRepository, roleMapper);
    }

    @Test
    @DisplayName("findRoleByName: Cache-miss nạp từ DB và lưu RAM, lần gọi sau trả thẳng từ RAM")
    void findRoleByName_CacheMissThenHit() {
        Role role = Role.builder().id(roleId).name(RoleName.ROLE_USER).level(10).build();
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(role));

        Optional<Role> firstCall = cacheHelper.findRoleByName(RoleName.ROLE_USER);
        assertThat(firstCall).contains(role);

        Optional<Role> secondCall = cacheHelper.findRoleByName(RoleName.ROLE_USER);
        assertThat(secondCall).contains(role);

        verify(roleRepository, times(1)).findByName(RoleName.ROLE_USER);
    }

    @Test
    @DisplayName("findRoleById: Cache-miss nạp từ DB và lưu RAM, lần gọi sau trả thẳng từ RAM")
    void findRoleById_CacheMissThenHit() {
        Role role = Role.builder().id(roleId).name(RoleName.ROLE_ADMIN).level(1).build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        Optional<Role> firstCall = cacheHelper.findRoleById(roleId);
        assertThat(firstCall).contains(role);

        Optional<Role> secondCall = cacheHelper.findRoleById(roleId);
        assertThat(secondCall).contains(role);

        verify(roleRepository, times(1)).findById(roleId);
    }

    @Test
    @DisplayName("findPermissionByName: Cache-miss nạp từ DB và lưu RAM, lần gọi sau trả thẳng từ RAM")
    void findPermissionByName_CacheMissThenHit() {
        Permission permission = Permission.builder().id(permId).name(PermissionName.USERS_READ).build();
        when(permissionRepository.findByName(PermissionName.USERS_READ)).thenReturn(Optional.of(permission));

        Optional<Permission> firstCall = cacheHelper.findPermissionByName(PermissionName.USERS_READ);
        assertThat(firstCall).contains(permission);

        Optional<Permission> secondCall = cacheHelper.findPermissionByName(PermissionName.USERS_READ);
        assertThat(secondCall).contains(permission);

        verify(permissionRepository, times(1)).findByName(PermissionName.USERS_READ);
    }

    @Test
    @DisplayName("findAllRoles: Cache-miss nạp toàn bộ roles từ DB, lần gọi sau lấy từ RAM")
    void findAllRoles_CacheMissThenHit() {
        Role role = Role.builder().id(roleId).name(RoleName.ROLE_USER).level(10).build();
        RoleResponse response = new RoleResponse(RoleName.ROLE_USER, 10, List.of(), "User role");

        when(roleRepository.findAll()).thenReturn(List.of(role));
        when(roleMapper.toResponse(role)).thenReturn(response);

        List<RoleResponse> firstCall = cacheHelper.findAllRoles();
        assertThat(firstCall).containsExactly(response);

        List<RoleResponse> secondCall = cacheHelper.findAllRoles();
        assertThat(secondCall).containsExactly(response);

        verify(roleRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findPermissionsByRole: Cache-miss nạp danh sách quyền, lần gọi sau lấy từ RAM")
    void findPermissionsByRole_CacheMissThenHit() {
        Role role = Role.builder().id(roleId).name(RoleName.ROLE_USER).level(10).build();
        PermissionResponse permResp = new PermissionResponse(PermissionName.USERS_READ, "Xem người dùng");

        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(role));
        when(roleMapper.mapPermissions(role)).thenReturn(List.of(permResp));

        List<PermissionResponse> firstCall = cacheHelper.findPermissionsByRole(RoleName.ROLE_USER);
        assertThat(firstCall).containsExactly(permResp);

        List<PermissionResponse> secondCall = cacheHelper.findPermissionsByRole(RoleName.ROLE_USER);
        assertThat(secondCall).containsExactly(permResp);

        verify(roleRepository, times(1)).findByName(RoleName.ROLE_USER);
    }

    @Test
    @DisplayName("evictPermissionsByRole: Xóa cache quyền thành công, lần gọi sau query lại DB")
    void evictPermissionsByRole_InvalidatesCache() {
        Role role = Role.builder().id(roleId).name(RoleName.ROLE_USER).level(10).build();
        PermissionResponse permResp = new PermissionResponse(PermissionName.USERS_READ, "Xem người dùng");

        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(role));
        when(roleMapper.mapPermissions(role)).thenReturn(List.of(permResp));

        cacheHelper.findPermissionsByRole(RoleName.ROLE_USER);
        verify(roleRepository, times(1)).findByName(RoleName.ROLE_USER);

        // hủy cache của ROLE_USER
        cacheHelper.evictPermissionsByRole(RoleName.ROLE_USER);

        cacheHelper.findPermissionsByRole(RoleName.ROLE_USER);
        verify(roleRepository, times(2)).findByName(RoleName.ROLE_USER);
    }
}
