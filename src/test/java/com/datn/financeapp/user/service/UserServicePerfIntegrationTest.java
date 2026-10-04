package com.datn.financeapp.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.performance.ServicePerfSupport;
import com.datn.financeapp.performance.ServicePerfSupport.Profile;
import com.datn.financeapp.performance.SqlCountingConfig;
import com.datn.financeapp.user.dto.request.AddPermissionRoleRequest;
import com.datn.financeapp.user.dto.request.BlockUserRequest;
import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.user.dto.request.UpdateProfileRequest;
import com.datn.financeapp.user.dto.request.UpdateUserRoleReq;
import com.datn.financeapp.user.dto.request.UserCreateReq;
import com.datn.financeapp.user.dto.request.UserGetReq;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.PermissionName;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Đo hiệu năng (số câu SQL + thời gian) của từng hàm trong module {@code user}: {@link UserService},
 * {@link UserBehavierService}, {@link ManagerUserService}, {@link ManagerRoleService}. Khung đo và cách đọc
 * báo cáo xem {@link ServicePerfSupport}.
 *
 * <p>Hồ sơ dữ liệu:
 * <ul>
 *   <li><b>Tốt nhất</b>: hệ thống chỉ có admin (có sẵn từ migration) và một người dùng thường; hàm đọc nhắm vào
 *       người dùng thường (một vai trò, ít quyền).</li>
 *   <li><b>Tệ nhất</b>: thêm {@value #MANY_USERS} người dùng thường; hàm đọc nhắm vào chính admin (hai vai trò,
 *       giữ đủ mọi quyền) và các hàm duyệt danh sách phải quét toàn bộ người dùng.</li>
 * </ul>
 * Hàm ghi và hàm xoá luôn chạy trên người dùng dựng riêng cho từng lần gọi nên không làm bẩn dữ liệu của hàm khác.
 *
 * <p>Không phải test hành vi, chỉ ghi báo cáo vào log và {@code target/perf-user.txt}. Chạy riêng:
 * {@code mvn test -Dtest=UserServicePerfIntegrationTest}
 */
@Slf4j
@Testcontainers
@SpringBootTest(classes = com.datn.financeapp.App.class)
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, SqlCountingConfig.class})
class UserServicePerfIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@financeapp.com";
    private static final String KNOWN_PASSWORD = "matkhau123";
    private static final int ADMIN_LEVEL = 1;
    private static final int USER_LEVEL = 10;
    static final int MANY_USERS = 100;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci11c2VyLXBlcmYtdGVzdC0wNTA1MDUwNQ==");
    }

    // hàm tạo mật khẩu và đăng ký gửi email thật qua SMTP, nên thay bằng mock
    @MockitoBean
    private EmailService emailService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserBehavierService userBehavierService;

    @Autowired
    private ManagerUserService managerUserService;

    @Autowired
    private ManagerRoleService managerRoleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ServicePerfSupport perf = new ServicePerfSupport("user");
    private final AtomicInteger emailCounter = new AtomicInteger();

    private UUID adminId;
    private UUID plainUserId;
    private String plainUserEmail;
    private String knownPasswordHash;
    private Role userRole;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Đo SQL và thời gian từng hàm của module user ở hồ sơ tốt nhất và tệ nhất")
    void measureUserModuleFunctions() throws Exception {
        knownPasswordHash = passwordEncoder.encode(KNOWN_PASSWORD);
        userRole = roleRepository.findByName(RoleName.ROLE_USER).orElseThrow();
        adminId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        User plain = createUser(true);
        plainUserId = plain.getId();
        plainUserEmail = plain.getEmail();
        actAsAdmin();

        measureAll(Profile.TOT_NHAT, plainUserId, plainUserEmail);

        seedManyUsers();
        measureAll(Profile.TE_NHAT, adminId, ADMIN_EMAIL);

        perf.writeReport("admin + 1 người dùng thường", "admin + " + (MANY_USERS + 1) + " người dùng thường");
        assertThat(perf.measuredFunctionCount()).isPositive();
    }

    // readSubject là người dùng mà các hàm đọc nhắm vào: người thường ở hồ sơ tốt nhất, admin ở hồ sơ tệ nhất
    private void measureAll(Profile profile, UUID readSubjectId, String readSubjectEmail) {
        List<UUID> allIds = userRepository.findAll().stream().map(User::getId).toList();
        AtomicReference<User> target = new AtomicReference<>();

        measureUserService(profile, readSubjectId, readSubjectEmail, allIds, target);
        measureUserBehavierService(profile, readSubjectId, target);
        measureManagerUserService(profile, target);
        measureManagerRoleService(profile);
    }

    private void measureUserService(
            Profile profile, UUID subjectId, String subjectEmail, List<UUID> allIds, AtomicReference<User> target) {
        perf.measure(profile, "UserService.create",
                () -> userService.create(UserCreateReq.forEmail(uniqueEmail(), knownPasswordHash, Instant.now())));
        perf.measure(profile, "UserService.get (theo id)", () -> userService.get(new UserGetReq(subjectId)));
        perf.measure(profile, "UserService.get (theo email)", () -> userService.get(new UserGetReq(subjectEmail)));
        perf.measure(profile, "UserService.getNames (theo danh sách id)", () -> userService.getNames(allIds));
        perf.measure(profile, "UserService.updateStatus",
                () -> userService.updateStatus(plainUserEmail, UserStatus.ACTIVE));
        perf.measure(profile, "UserService.updatePass",
                () -> userService.updatePass(plainUserEmail, "matkhauMoi" + emailCounter.incrementAndGet()));
        perf.measure(profile, "UserService.resolveGoogleUser (người dùng mới)",
                () -> userService.resolveGoogleUser(uniqueEmail(), "google-" + UUID.randomUUID(), Instant.now()));
        perf.measure(profile, "UserService.resolveGoogleUser (người dùng đã có)",
                () -> target.set(createUser(true)),
                () -> userService.resolveGoogleUser(target.get().getEmail(), "google-" + UUID.randomUUID(), Instant.now()));
        perf.measure(profile, "UserService.existByEmail", () -> userService.existByEmail(subjectEmail));
        perf.measure(profile, "UserService.validUser", () -> userService.validUser(subjectEmail));
    }

    private void measureUserBehavierService(Profile profile, UUID subjectId, AtomicReference<User> target) {
        perf.measure(profile, "UserBehavierService.getMe", () -> userBehavierService.getMe(subjectId));
        perf.measure(profile, "UserBehavierService.updateMe",
                () -> userBehavierService.updateMe(plainUserId, new UpdateProfileRequest("Đo", "Hiệu năng", null)));
        perf.measure(profile, "UserBehavierService.changePassword",
                () -> target.set(createUser(true)),
                () -> userBehavierService.changePassword(
                        target.get().getId(), new UpdatePassReq(KNOWN_PASSWORD, "matkhauMoi456")));
        perf.measure(profile, "UserBehavierService.createPassword",
                () -> target.set(createUserWithoutPassword()),
                () -> userBehavierService.createPassword(target.get().getId()));
        // deleteMe lấy người dùng từ SecurityContext nên phải đổi người hành động rồi trả về admin
        perf.measure(profile, "UserBehavierService.deleteMe",
                () -> target.set(createUser(true)),
                () -> {
                    actAs(target.get().getId(), USER_LEVEL);
                    try {
                        userBehavierService.deleteMe(KNOWN_PASSWORD);
                    } finally {
                        actAsAdmin();
                    }
                });
    }

    private void measureManagerUserService(Profile profile, AtomicReference<User> target) {
        perf.measure(profile, "ManagerUserService.findByUserId", () -> managerUserService.findByUserId(plainUserId));
        perf.measure(profile, "ManagerUserService.findAllUser", () -> managerUserService.findAllUser());
        perf.measure(profile, "ManagerUserService.lockUser",
                () -> target.set(createUser(true)),
                () -> managerUserService.lockUser(adminId, new BlockUserRequest(target.get().getId(), "đo hiệu năng")));
        perf.measure(profile, "ManagerUserService.addRoleToUser",
                () -> target.set(createUser(false)),
                () -> managerUserService.addRoleToUser(new UpdateUserRoleReq(target.get().getId(), RoleName.ROLE_USER)));
        // hiện chỉ có ROLE_ADMIN (cấp ngang admin nên bị chặn cấp bậc) và ROLE_USER (vai trò mặc định, không được gỡ),
        // nên không còn lệnh gọi nào thành công để đo; bỏ chú thích này khi hệ thống có thêm vai trò trung gian
        perf.skip("ManagerUserService.removeRoleToUser", "chưa có vai trò nào gỡ được (ROLE_USER là mặc định, ROLE_ADMIN ngang cấp admin)");
        perf.measure(profile, "ManagerUserService.deleteByUserId",
                () -> target.set(createUser(true)),
                () -> managerUserService.deleteByUserId(target.get().getId()));
    }

    private void measureManagerRoleService(Profile profile) {
        // gỡ sẵn quyền khỏi ROLE_USER trước mỗi lần gọi để hàm thật sự phải chèn bản ghi mới
        perf.measure(profile, "ManagerRoleService.addPermissionToRole (theo id vai trò)",
                () -> removePermissionFromUserRole(PermissionName.USERS_READ),
                () -> managerRoleService.addPermissionToRole(userRole.getId(), PermissionName.USERS_READ));
        perf.measure(profile, "ManagerRoleService.addPermissionToRole (theo request)",
                () -> removePermissionFromUserRole(PermissionName.USERS_READ),
                () -> managerRoleService.addPermissionToRole(
                        new AddPermissionRoleRequest(RoleName.ROLE_USER, PermissionName.USERS_READ)));
        perf.measure(profile, "ManagerRoleService.findRoles", () -> managerRoleService.findRoles());
        perf.measure(profile, "ManagerRoleService.findByRole (ROLE_ADMIN, đủ mọi quyền)",
                () -> managerRoleService.findByRole(RoleName.ROLE_ADMIN));
        perf.measure(profile, "ManagerRoleService.findByRole (ROLE_USER)",
                () -> managerRoleService.findByRole(RoleName.ROLE_USER));
    }

    private void removePermissionFromUserRole(PermissionName permission) {
        jdbcTemplate.update(
                "DELETE FROM role_permissions WHERE role_id = ? AND permission_id = (SELECT id FROM permissions WHERE name = ?)",
                userRole.getId(), permission.getValue());
    }

    // người dùng thường có hoặc không có vai trò ROLE_USER, mật khẩu là KNOWN_PASSWORD
    private User createUser(boolean withRole) {
        User user = userRepository.save(newUserEntity(uniqueEmail(), knownPasswordHash));
        if (withRole)
            assignUserRole(user);
        return user;
    }

    private User createUserWithoutPassword() {
        User user = userRepository.save(newUserEntity(uniqueEmail(), null));
        assignUserRole(user);
        return user;
    }

    private User newUserEntity(String email, String passwordHash) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password(passwordHash)
                .firstName("Đo")
                .lastName("Hiệu năng")
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
    }

    private void assignUserRole(User user) {
        UserRole link = new UserRole(user.getId(), userRole.getId());
        link.setUser(user);
        link.setRole(userRole);
        userRoleRepository.save(link);
    }

    private void seedManyUsers() {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < MANY_USERS; i++)
            users.add(newUserEntity(uniqueEmail(), knownPasswordHash));
        userRepository.saveAll(users);
        users.forEach(this::assignUserRole);
    }

    private String uniqueEmail() {
        return "perf.user." + emailCounter.incrementAndGet() + "." + UUID.randomUUID() + "@example.com";
    }

    private void actAsAdmin() {
        actAs(adminId, ADMIN_LEVEL);
    }

    // currentUserId lấy từ principal, currentLevel lấy từ details — khớp JwtAuthFilter
    private void actAs(UUID userId, int level) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
        authentication.setDetails(level);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
