package com.datn.financeapp.user;

import com.datn.financeapp.user.entity.Permission;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.RolePermission;
import com.datn.financeapp.user.enums.PermissionName;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.repository.PermissionRepository;
import com.datn.financeapp.user.repository.RolePermissionRepository;
import com.datn.financeapp.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
@Profile("!test")
public class AppRunner implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        log.info("AppRunner: Khởi tạo/đồng bộ roles và permissions...");
        initPermissions();
        initRoles();
        assignPermissionsToAdmin();
        initAdminAccount();
        log.info("AppRunner: Hoàn tất khởi tạo roles, permissions và tài khoản admin.");
    }

    private void initAdminAccount() {
        String adminEmail = "admin";
        User admin = userRepository.findByEmail(adminEmail).orElseGet(() -> {
            User newUser = User.builder()
                    .id(UUID.randomUUID())
                    .email(adminEmail)
                    .password(passwordEncoder.encode("admin"))
                    .firstName("System")
                    .lastName("Admin")
                    .plan(UserPlan.PREMIUM)
                    .status(UserStatus.ACTIVE)
                    .isDeleted(false)
                    .createdAt(Instant.now())
                    .build();
            User saved = userRepository.save(newUser);
            log.info("AppRunner: Đã tạo tài khoản admin mặc định: {} / admin", adminEmail);
            return saved;
        });

        for (RoleName roleName : List.of(RoleName.ROLE_ADMIN, RoleName.ROLE_USER)) {
            roleRepository.findByName(roleName).ifPresent(role -> {
                if (!userRoleRepository.existsByUserIdAndRoleId(admin.getId(), role.getId())) {
                    UserRole ur = new UserRole(admin.getId(), role.getId());
                    ur.setUser(admin);
                    ur.setRole(role);
                    userRoleRepository.save(ur);
                    log.info("AppRunner: Đã gán role {} cho tài khoản {}", role.getName().name(), adminEmail);
                }
            });
        }
    }

    private void initPermissions() {
        for (PermissionName permName : PermissionName.values()) {
            if (!permissionRepository.existsByName(permName)) {
                Permission permission = Permission.builder()
                        .id(UUID.randomUUID())
                        .name(permName)
                        .description(permName.getDescription())
                        .createdAt(Instant.now())
                        .build();
                permissionRepository.save(permission);
                log.info("Đã tạo permission: {}", permName.getValue());
            }
        }
    }

    private void initRoles() {
        for (RoleName roleName : RoleName.values()) {
            int expectedLevel = resolveRoleLevel(roleName);
            roleRepository.findByName(roleName).ifPresentOrElse(
                    existing -> {
                        if (existing.getLevel() != expectedLevel) {
                            existing.setLevel(expectedLevel);
                            roleRepository.save(existing);
                            log.info("AppRunner: Cập nhật level role {}: {}", roleName.name(), expectedLevel);
                        }
                    },
                    () -> {
                        Role role = Role.builder()
                                .id(UUID.randomUUID())
                                .name(roleName)
                                .level(expectedLevel)
                                .description(resolveRoleDescription(roleName))
                                .createdAt(Instant.now())
                                .build();
                        roleRepository.save(role);
                        log.info("AppRunner: Đã tạo role: {} với level: {}", roleName.name(), expectedLevel);
                    }
            );
        }
    }

    private void assignPermissionsToAdmin() {
        roleRepository.findByName(RoleName.ROLE_ADMIN).ifPresent(adminRole -> {
            List<Permission> allPermissions = permissionRepository.findAll();
            for (Permission p : allPermissions) {
                if (!rolePermissionRepository.existsByRoleIdAndPermissionId(adminRole.getId(), p.getId())) {
                    RolePermission rp = new RolePermission(adminRole.getId(), p.getId());
                    rp.setRole(adminRole);
                    rp.setPermission(p);
                    rolePermissionRepository.save(rp);
                    log.info("Đã gán permission {} cho role {}", p.getName().getValue(), adminRole.getName().name());
                }
            }
        });
    }

    private int resolveRoleLevel(RoleName roleName) {
        return switch (roleName) {
            case ROLE_ADMIN -> 1;
            case ROLE_USER -> 10;
            default -> 100;
        };
    }

    private String resolveRoleDescription(RoleName roleName) {
        return switch (roleName) {
            case ROLE_ADMIN -> "Quản trị viên toàn quyền hệ thống";
            case ROLE_USER -> "Người dùng ứng dụng thông thường";
            default -> roleName.name();
        };
    }
}
