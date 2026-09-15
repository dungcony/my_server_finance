package com.datn.financeapp.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hdXRoLXByb2ZpbGUtdGVzdC0zMmI=");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @BeforeEach
    void cleanTables() {
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser(String email, String googleId, UserStatus status) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email.toLowerCase().trim())
                .password("hashed_password")
                .firstName("Test")
                .lastName("User")
                .googleId(googleId)
                .plan(UserPlan.FREE)
                .status(status)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build();
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("findByEmail & existsByEmail: Tìm thấy khi email tồn tại")
    void findByEmail_And_ExistsByEmail() {
        User user = createUser("repo.test@example.com", null, UserStatus.ACTIVE);

        Optional<User> found = userRepository.findByEmail("repo.test@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());

        assertThat(userRepository.existsByEmail("repo.test@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nonexistent@example.com")).isFalse();
    }

    @Test
    @DisplayName("findByGoogleId: Tìm thấy user liên kết tài khoản Google")
    void findByGoogleId_ReturnsUser() {
        User user = createUser("google.user@example.com", "google-sub-9999", UserStatus.ACTIVE);

        Optional<User> found = userRepository.findByGoogleId("google-sub-9999");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());

        assertThat(userRepository.findByGoogleId("wrong-google-id")).isEmpty();
    }

    @Test
    @DisplayName("findAuthoritiesByUserId: Trả về danh sách authority gồm tên Role và Permission gộp lại (UNION)")
    void findAuthoritiesByUserId_ReturnsUnionOfRolesAndPermissions() {
        User user = createUser("authority.test@example.com", null, UserStatus.ACTIVE);
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();

        userRoleRepository.saveAndFlush(new UserRole(user.getId(), adminRole.getId()));

        List<String> authorities = userRepository.findAuthoritiesByUserId(user.getId());
        assertThat(authorities).isNotEmpty();
        assertThat(authorities).contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("findTopRoleLevelByUserId: Trả về level nhỏ nhất (quyền cao nhất), fallback về MAX_VALUE nếu không có role")
    void findTopRoleLevelByUserId_ReturnsMinLevelOrMaxInt() {
        User userWithoutRole = createUser("no.role@example.com", null, UserStatus.ACTIVE);
        int defaultLevel = userRepository.findTopRoleLevelByUserId(userWithoutRole.getId());
        assertThat(defaultLevel).isEqualTo(Integer.MAX_VALUE);

        User userWithRole = createUser("has.role@example.com", null, UserStatus.ACTIVE);
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(userWithRole.getId(), userRole.getId()));

        int level = userRepository.findTopRoleLevelByUserId(userWithRole.getId());
        assertThat(level).isEqualTo(userRole.getLevel());
    }

    @Test
    @DisplayName("findAllVisibleToLevel: Chỉ hiển thị user có role yếu hơn callerLevel (level > callerLevel)")
    void findAllVisibleToLevel_FiltersOutSuperiorOrEqualRoles() {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER).orElseThrow();

        User adminUser = createUser("admin.visible@example.com", null, UserStatus.ACTIVE);
        userRoleRepository.saveAndFlush(new UserRole(adminUser.getId(), adminRole.getId()));

        User regularUser = createUser("user.visible@example.com", null, UserStatus.ACTIVE);
        userRoleRepository.saveAndFlush(new UserRole(regularUser.getId(), userRole.getId()));

        List<User> visible = userRepository.findAllVisibleToLevel(adminRole.getLevel());
        List<UUID> visibleIds = visible.stream().map(User::getId).toList();

        assertThat(visibleIds).contains(regularUser.getId());
        assertThat(visibleIds).doesNotContain(adminUser.getId());
    }

    @Test
    @DisplayName("setStatusById: Cập nhật status trực tiếp qua @Modifying")
    void setStatusById_UpdatesDirectly() {
        User user = createUser("status.change@example.com", null, UserStatus.PENDING_VERIFY);
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFY);

        userRepository.setStatusById(user.getId(), UserStatus.BLOCKED);

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    @DisplayName("findAll: Chỉ trả về user chưa bị xóa (isDeleted = false) và fetch sẵn userRoles")
    void findAll_FiltersOutDeletedUsers() {
        User activeUser = createUser("active.repo@example.com", null, UserStatus.ACTIVE);
        User deletedUser = User.builder()
                .id(UUID.randomUUID())
                .email("deleted.repo@example.com")
                .password("hashed_password")
                .firstName("Deleted")
                .lastName("User")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(true)
                .createdAt(Instant.now())
                .build();
        userRepository.saveAndFlush(deletedUser);

        List<User> all = userRepository.findAll();
        List<UUID> ids = all.stream().map(User::getId).toList();

        assertThat(ids).contains(activeUser.getId());
        assertThat(ids).doesNotContain(deletedUser.getId());
    }
}
