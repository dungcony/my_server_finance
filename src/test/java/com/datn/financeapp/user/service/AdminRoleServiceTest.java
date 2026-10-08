package com.datn.financeapp.user.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.user.dto.request.AddPermissionRoleRequest;
import com.datn.financeapp.user.entity.Permission;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.RolePermission;
import com.datn.financeapp.user.enums.PermissionName;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.helper.RolePermissionCacheHelper;
import com.datn.financeapp.user.helper.UserLevelCacheHelper;
import com.datn.financeapp.user.mapper.RoleMapper;
import com.datn.financeapp.user.repository.PermissionRepository;
import com.datn.financeapp.user.repository.RolePermissionRepository;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.service.impl.ManagerRoleServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRoleServiceTest {

    private static final int ADMIN_LEVEL = 1;
    private static final int USER_LEVEL = 10;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleMapper roleMapper;

    private ManagerRoleServiceImpl adminRoleService;

    private final UUID roleId = UUID.randomUUID();
    private final UUID permId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RolePermissionCacheHelper rolePermissionCacheHelper = new RolePermissionCacheHelper(roleRepository, permissionRepository, roleMapper);
        UserLevelCacheHelper userLevelCacheHelper = new UserLevelCacheHelper(userRepository);
        adminRoleService = new ManagerRoleServiceImpl(rolePermissionRepository, userRepository, rolePermissionCacheHelper, userLevelCacheHelper);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(callerId.toString(), null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
        Role role = roleWithLevel(RoleName.ROLE_USER, USER_LEVEL);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        callerHasTopLevel(ADMIN_LEVEL);
        when(permissionRepository.findByName(PermissionName.USERS_READ)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminRoleService.addPermissionToRole(roleId, PermissionName.USERS_READ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());

        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC_ROLE_SVC_03: Gán permission thành công -> Lưu RolePermission")
    void addPermissionToRole_Success_SavesRolePermission() {
        Role role = roleWithLevel(RoleName.ROLE_USER, USER_LEVEL);
        Permission permission = permission(PermissionName.USERS_READ);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        callerHasTopLevel(ADMIN_LEVEL);
        when(permissionRepository.findByName(PermissionName.USERS_READ)).thenReturn(Optional.of(permission));
        callerHasAuthorities(PermissionName.USERS_READ.getValue());
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permId)).thenReturn(false);

        adminRoleService.addPermissionToRole(roleId, PermissionName.USERS_READ);

        verify(rolePermissionRepository).save(any(RolePermission.class));
    }

    @Test
    @DisplayName("TC_ROLE_SVC_04: Gán bằng tên role (RoleName) -> Thành công")
    void addPermissionToRole_ByRoleName_Success() {
        Role role = roleWithLevel(RoleName.ROLE_USER, USER_LEVEL);
        Permission permission = permission(PermissionName.USERS_READ);

        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(role));
        callerHasTopLevel(ADMIN_LEVEL);
        when(permissionRepository.findByName(PermissionName.USERS_READ)).thenReturn(Optional.of(permission));
        callerHasAuthorities(PermissionName.USERS_READ.getValue());
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permId)).thenReturn(false);

        adminRoleService.addPermissionToRole(new AddPermissionRoleRequest(RoleName.ROLE_USER, PermissionName.USERS_READ));

        verify(rolePermissionRepository).save(any(RolePermission.class));
    }

    @Test
    @DisplayName("TC_ROLE_SVC_05: Gán permission cho role ngang cấp mình -> FORBIDDEN, không lưu")
    void addPermissionToRole_RoleSameLevelAsCaller_ThrowsForbidden() {
        Role role = roleWithLevel(RoleName.ROLE_ADMIN, ADMIN_LEVEL);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        callerHasTopLevel(ADMIN_LEVEL);

        assertThatThrownBy(() -> adminRoleService.addPermissionToRole(roleId, PermissionName.USERS_READ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

        verifyNoInteractions(permissionRepository, rolePermissionRepository);
    }

    @Test
    @DisplayName("TC_ROLE_SVC_06: Gán permission cho role cao cấp hơn mình -> FORBIDDEN, không lưu")
    void addPermissionToRole_RoleHigherThanCaller_ThrowsForbidden() {
        Role role = roleWithLevel(RoleName.ROLE_ADMIN, ADMIN_LEVEL);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        callerHasTopLevel(5);

        assertThatThrownBy(() -> adminRoleService.addPermissionToRole(roleId, PermissionName.USERS_READ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

        verifyNoInteractions(permissionRepository, rolePermissionRepository);
    }

    @Test
    @DisplayName("TC_ROLE_SVC_07: Người gọi không có role nào (level yếu nhất) -> FORBIDDEN")
    void addPermissionToRole_CallerHasNoRole_ThrowsForbidden() {
        Role role = roleWithLevel(RoleName.ROLE_USER, USER_LEVEL);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        callerHasTopLevel(Integer.MAX_VALUE);

        assertThatThrownBy(() -> adminRoleService.addPermissionToRole(roleId, PermissionName.USERS_READ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

        verifyNoInteractions(permissionRepository, rolePermissionRepository);
    }

    @Test
    @DisplayName("TC_ROLE_SVC_08: Gán permission mà chính mình không có -> FORBIDDEN, không lưu")
    void addPermissionToRole_CallerLacksPermission_ThrowsForbidden() {
        Role role = roleWithLevel(RoleName.ROLE_USER, USER_LEVEL);
        Permission permission = permission(PermissionName.USERS_DELETE);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        callerHasTopLevel(5);
        when(permissionRepository.findByName(PermissionName.USERS_DELETE)).thenReturn(Optional.of(permission));
        callerHasAuthorities(PermissionName.ROLE_PERMISSION_UPDATE.getValue(), RoleName.ROLE_USER.name());

        assertThatThrownBy(() -> adminRoleService.addPermissionToRole(roleId, PermissionName.USERS_DELETE))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC_ROLE_SVC_09: Permission đã gán cho role từ trước -> USER_ROLE_PERMISSION_ALREADY_ASSIGNED, không lưu trùng")
    void addPermissionToRole_AlreadyAssigned_ThrowsConflict() {
        Role role = roleWithLevel(RoleName.ROLE_USER, USER_LEVEL);
        Permission permission = permission(PermissionName.USERS_READ);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        callerHasTopLevel(ADMIN_LEVEL);
        when(permissionRepository.findByName(PermissionName.USERS_READ)).thenReturn(Optional.of(permission));
        callerHasAuthorities(PermissionName.USERS_READ.getValue());
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permId)).thenReturn(true);

        assertThatThrownBy(() -> adminRoleService.addPermissionToRole(roleId, PermissionName.USERS_READ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.USER_ROLE_PERMISSION_ALREADY_ASSIGNED.getCode());

        verify(rolePermissionRepository, never()).save(any());
    }

    private Role roleWithLevel(RoleName name, int level) {
        return Role.builder().id(roleId).name(name).level(level).build();
    }

    private Permission permission(PermissionName name) {
        return Permission.builder().id(permId).name(name).build();
    }

    private void callerHasTopLevel(int level) {
        when(userRepository.findTopRoleLevelByUserId(callerId)).thenReturn(level);
    }

    private void callerHasAuthorities(String... authorities) {
        when(userRepository.findAuthoritiesByUserId(callerId)).thenReturn(List.of(authorities));
    }
}
