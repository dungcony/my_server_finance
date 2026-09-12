package com.datn.financeapp.user;

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
import com.datn.financeapp.user.mapper.UserMapper;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import com.datn.financeapp.user.service.impl.ManagerAccountServiceImpl;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private AuthService authService;

    @Mock
    private BlacklistedUserRepository blacklistedUserRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private ManagerAccountServiceImpl adminUserService;

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
        Role adminRole = Role.builder().id(UUID.randomUUID()).name(RoleName.ROLE_ADMIN).level(level).build();
        UserRole adminUserRole = new UserRole(adminId, adminRole.getId());
        adminUserRole.setRole(adminRole);
        User admin = User.builder().id(adminId).email("admin@example.com").isDeleted(false).build();
        admin.setUserRoles(Set.of(adminUserRole));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
    }

    @Test
    @DisplayName("TC_UNIT_01: Admin không được phép tự khóa chính mình (VALIDATION_ERROR)")
    void blockUser_SelfBlock_ThrowsValidationError() {
        BlockUserRequest req = new BlockUserRequest(adminId, "Tự khóa");

        assertThatThrownBy(() -> adminUserService.blockUser(req))
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

        assertThatThrownBy(() -> adminUserService.blockUser(req))
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

        assertThatThrownBy(() -> adminUserService.blockUser(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode())
                .hasMessageContaining("Không tìm thấy người dùng.");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authService, blacklistedUserRepository);
    }

    @Test
    @DisplayName("TC_UNIT_04: Khóa thành công - Cập nhật status BLOCKED, blacklist Redis, revoke tokens")
    void blockUser_Success_UpdatesStatusAndRevokesTokens() {
        stubAdminWithLevel(1);
        User user = User.builder()
                .id(targetUserId)
                .email("victim@example.com")
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .build();

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));

        BlockUserRequest req = new BlockUserRequest(targetUserId, "Spam hệ thống");
        adminUserService.blockUser(req);

        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
        verify(userRepository).save(user);
        verify(blacklistedUserRepository).blacklist(targetUserId, "Spam hệ thống", 3600L);
        verify(authService).revokeAllTokensForUser(targetUserId);
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
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(role));
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
        when(roleRepository.findByName(RoleName.ROLE_ADMIN)).thenReturn(Optional.of(adminRoleTarget));

        assertThatThrownBy(() -> adminUserService.addRoleToUser(new UpdateUserRoleReq(targetUserId, RoleName.ROLE_ADMIN)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

        verify(userRoleRepository, never()).save(any());
    }
}
