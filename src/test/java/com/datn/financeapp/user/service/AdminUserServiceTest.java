package com.datn.financeapp.user.service;

import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.BlacklistedUserRepository;
import com.datn.financeapp.user.dto.request.BlockUserRequest;
import com.datn.financeapp.user.dto.request.UpdateUserRoleReq;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.event.publiser.UserLockedEvent;
import com.datn.financeapp.user.helper.RolePermissionCacheHelper;
import com.datn.financeapp.user.helper.UserAuthCacheHelper;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import com.datn.financeapp.user.service.impl.ManagerUserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RolePermissionCacheHelper rolePermissionCacheHelper;
    @Mock
    private UserAuthCacheHelper userAuthCacheHelper;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private AuthService authService;

    @Mock
    private BlacklistedUserRepository blacklistedUserRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;


    @InjectMocks
    private ManagerUserServiceImpl adminUserService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                adminId.toString(), null, Collections.emptyList()
        );
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Admin gán role level 1 (ROLE_ADMIN) cho chính mình trong mock repo, để pass được
    // guard "không được gán role có cấp bậc cao hơn hoặc bằng chính mình" khi test gán ROLE_USER.
    private void stubAdminWithLevel(int level) {
        when(userAuthCacheHelper.getUserLevel(adminId)).thenReturn(level);
    }

    @Test
    @DisplayName("TC_UNIT_01: Admin không được phép tự khóa chính mình (VALIDATION_ERROR)")
    void blockUser_SelfBlock_ThrowsValidationError() {
        BlockUserRequest req = new BlockUserRequest(adminId, "Tự khóa");

        assertThatThrownBy(() -> adminUserService.lockUser(adminId, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode())
                .hasMessageContaining("Không được phép tự khóa tài khoản của chính mình.");

        verifyNoInteractions(userRepository, authService, blacklistedUserRepository);
    }

    @Test
    @DisplayName("TC_UNIT_02: Không tìm thấy người dùng (NOT_FOUND)")
    void blockUser_UserNotFound_ThrowsNotFound() {
        when(userRepository.findById(targetUserId)).thenReturn(Optional.empty());
        BlockUserRequest req = new BlockUserRequest(targetUserId, "Vi phạm chính sách");

        assertThatThrownBy(() -> adminUserService.lockUser(adminId, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode())
                .hasMessageContaining("Không tìm thấy người dùng.");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authService, blacklistedUserRepository);
    }

    @Test
    @DisplayName("TC_UNIT_03: Người dùng đã bị xóa mềm coi như không tìm thấy (NOT_FOUND)")
    void blockUser_UserDeleted_ThrowsNotFound() {
        User deletedUser = User.builder()
                .id(targetUserId)
                .email("deleted@example.com")
                .isDeleted(true)
                .build();

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(deletedUser));
        BlockUserRequest req = new BlockUserRequest(targetUserId, "Vi phạm");

        assertThatThrownBy(() -> adminUserService.lockUser(adminId, req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode())
                .hasMessageContaining("Không tìm thấy người dùng.");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authService, blacklistedUserRepository);
    }

    @Test
    @DisplayName("TC_UNIT_04: Khóa thành công - Cập nhật status BLOCKED, blacklist Redis, phát sự kiện khoá để thu hồi phiên")
    void blockUser_Success_UpdatesStatusAndRevokesTokens() {
        stubAdminWithLevel(1);
        when(userAuthCacheHelper.getUserLevel(targetUserId)).thenReturn(10);

        User user = User.builder()
                .id(targetUserId)
                .email("victim@example.com")
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .build();

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));

        BlockUserRequest req = new BlockUserRequest(targetUserId, "Spam hệ thống");
        adminUserService.lockUser(adminId, req);

        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
        verify(userRepository).save(user);
        verify(blacklistedUserRepository).add(targetUserId, "Spam hệ thống", 3600L);
        // thu hồi token do AuthEventListener xử lý khi nhận sự kiện, service chỉ cần phát đúng sự kiện
        verify(eventPublisher).publishEvent(new UserLockedEvent(targetUserId));
    }

    @Test
    @DisplayName("TC_UNIT_05: Gán role khi không tìm thấy user -> NOT_FOUND")
    void addRoleToUser_UserNotFound_ThrowsNotFound() {
        when(userRepository.findById(targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.addRoleToUser(new UpdateUserRoleReq(targetUserId, RoleName.ROLE_USER)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());

        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC_UNIT_06: Gán role thành công -> Lưu vào userRoleRepository")
    void addRoleToUser_Success_SavesUserRole() {
        User user = User.builder()
                .id(targetUserId)
                .email("user@example.com")
                .isDeleted(false)
                .build();
        // ROLE_ADMIN (level 1) là cấp cao nhất — không ai gán được nó qua endpoint này (kể cả
        // chính admin khác), nên test gán role phải dùng ROLE_USER (level 10, yếu hơn admin).
        Role role = Role.builder()
                .id(UUID.randomUUID())
                .name(RoleName.ROLE_USER)
                .level(10)
                .build();

        stubAdminWithLevel(1);
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(rolePermissionCacheHelper.findRoleByName(RoleName.ROLE_USER)).thenReturn(Optional.of(role));
        when(userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId())).thenReturn(false);

        adminUserService.addRoleToUser(new UpdateUserRoleReq(targetUserId, RoleName.ROLE_USER));

        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    @DisplayName("TC_UNIT_07: Không được gán role cấp bậc cao hơn hoặc bằng chính mình -> FORBIDDEN")
    void addRoleToUser_RoleLevelTooHigh_ThrowsForbidden() {
        User user = User.builder()
                .id(targetUserId)
                .email("user@example.com")
                .isDeleted(false)
                .build();
        Role adminRoleTarget = Role.builder()
                .id(UUID.randomUUID())
                .name(RoleName.ROLE_ADMIN)
                .level(1)
                .build();

        stubAdminWithLevel(1);
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(rolePermissionCacheHelper.findRoleByName(RoleName.ROLE_ADMIN)).thenReturn(Optional.of(adminRoleTarget));

        assertThatThrownBy(() -> adminUserService.addRoleToUser(new UpdateUserRoleReq(targetUserId, RoleName.ROLE_ADMIN)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC_UNIT_12: Gán vai trò người dùng đã có -> USER_ROLE_ALREADY_ASSIGNED, không lưu trùng")
    void addRoleToUser_AlreadyAssigned_ThrowsConflict() {
        User user = User.builder().id(targetUserId).email("user@example.com").isDeleted(false).build();
        Role role = Role.builder().id(UUID.randomUUID()).name(RoleName.ROLE_USER).level(10).build();

        stubAdminWithLevel(1);
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(rolePermissionCacheHelper.findRoleByName(RoleName.ROLE_USER)).thenReturn(Optional.of(role));
        when(userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId())).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.addRoleToUser(new UpdateUserRoleReq(targetUserId, RoleName.ROLE_USER)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.USER_ROLE_ALREADY_ASSIGNED.getCode());

        verify(userRoleRepository, never()).save(any());
    }

    // người dùng đích và role đích dùng chung cho các test thu hồi vai trò
    private Role stubRemovalTarget(RoleName roleName, int roleLevel) {
        User user = User.builder().id(targetUserId).email("user@example.com").isDeleted(false).build();
        Role role = Role.builder().id(UUID.randomUUID()).name(roleName).level(roleLevel).build();
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(rolePermissionCacheHelper.findRoleByName(roleName)).thenReturn(Optional.of(role));
        return role;
    }

    @Test
    @DisplayName("TC_UNIT_08: Không được thu hồi vai trò mặc định ROLE_USER -> USER_DEFAULT_ROLE_NOT_REMOVABLE")
    void removeRoleToUser_DefaultRole_ThrowsDefaultRoleNotRemovable() {
        stubAdminWithLevel(1);
        stubRemovalTarget(RoleName.ROLE_USER, 10);

        assertThatThrownBy(() -> adminUserService.removeRoleToUser(new UpdateUserRoleReq(targetUserId, RoleName.ROLE_USER)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.USER_DEFAULT_ROLE_NOT_REMOVABLE.getCode());

        verify(userRoleRepository, never()).deleteByUserIdAndRoleId(any(), any());
    }

    @Test
    @DisplayName("TC_UNIT_09: Thu hồi vai trò không phải mặc định (cấp thấp hơn người gọi) -> xoá bản ghi như cũ")
    void removeRoleToUser_NonDefaultRoleBelowCaller_DeletesLink() {
        // vai trò giả định cấp 1 còn người gọi cấp 0, vì hệ thống hiện chưa có vai trò trung gian nào
        stubAdminWithLevel(0);
        Role role = stubRemovalTarget(RoleName.ROLE_ADMIN, 1);
        when(userRoleRepository.existsByUserIdAndRoleId(targetUserId, role.getId())).thenReturn(true);

        adminUserService.removeRoleToUser(new UpdateUserRoleReq(targetUserId, RoleName.ROLE_ADMIN));

        verify(userRoleRepository).deleteByUserIdAndRoleId(targetUserId, role.getId());
    }

    @Test
    @DisplayName("TC_UNIT_10: Thu hồi vai trò cấp cao hơn hoặc bằng chính mình -> FORBIDDEN theo cấp bậc như cũ")
    void removeRoleToUser_RoleLevelTooHigh_ThrowsForbidden() {
        stubAdminWithLevel(1);
        stubRemovalTarget(RoleName.ROLE_ADMIN, 1);

        assertThatThrownBy(() -> adminUserService.removeRoleToUser(new UpdateUserRoleReq(targetUserId, RoleName.ROLE_ADMIN)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

        verify(userRoleRepository, never()).deleteByUserIdAndRoleId(any(), any());
    }

    @Test
    @DisplayName("TC_UNIT_11: Người gọi cùng cấp với ROLE_USER thì nhận lỗi cấp bậc trước, không lộ lỗi vai trò mặc định")
    void removeRoleToUser_CallerSameLevelAsDefaultRole_ThrowsLevelForbiddenFirst() {
        stubAdminWithLevel(10);
        stubRemovalTarget(RoleName.ROLE_USER, 10);

        assertThatThrownBy(() -> adminUserService.removeRoleToUser(new UpdateUserRoleReq(targetUserId, RoleName.ROLE_USER)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

        verify(userRoleRepository, never()).deleteByUserIdAndRoleId(any(), any());
    }
}

