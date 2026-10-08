package com.datn.financeapp.group.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionListRes;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.performance.SqlCountingConfig;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chặn N+1 ở các danh sách giao dịch nhóm: số câu SQL của {@code list}, {@code myList}, {@code listPending} không được
 * tăng theo số giao dịch trong trang. Nguyên nhân từng gặp: {@code GTransaction.participants} là collection lazy nên
 * mỗi giao dịch kéo thêm một câu nạp người chia tiền (6 SQL ở 10 giao dịch thành 103 SQL ở 100 giao dịch).
 * Chạy riêng: {@code mvn test -Dtest=GroupTransactionListQueryCountIntegrationTest}
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, SqlCountingConfig.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class GroupTransactionListQueryCountIntegrationTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int FEW = 10;
    private static final int MANY = 100;
    // chênh lệch cho phép khi tăng số giao dịch: tối đa hai câu nạp người chia tiền thêm (do default_batch_fetch_size = 50)
    private static final long ALLOWED_GROWTH = 2;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1saXN0LXNxbC1jb3VudC0wOTA5MDkwOQ==");
    }

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GTransactionService gTransactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID memberId;
    private UUID groupId;
    private UUID categoryId;

    @BeforeEach
    void createGroup() {
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER).orElseThrow();
        ownerId = createUser(userRole);
        memberId = createUser(userRole);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = 'expense' LIMIT 1", UUID.class);
        groupId = groupService.create(ownerId, new GroupCreateReq(
                "Nhóm đếm SQL", "Chặn N+1 danh sách giao dịch", 10_000_000L, true, true, List.of(memberId))).id();
    }

    @Test
    @DisplayName("list: số câu SQL không tăng theo số giao dịch")
    void list_SqlCountDoesNotGrowWithTransactionCount() {
        createExpenses(ownerId, FEW);
        long few = sqlOf(() -> gTransactionService.list(ownerId, groupId, firstPage(100)));

        createExpenses(ownerId, MANY - FEW);
        GroupTransactionListRes[] lastResult = new GroupTransactionListRes[1];
        long many = sqlOf(() -> lastResult[0] = gTransactionService.list(ownerId, groupId, firstPage(100)));

        assertThat(many).isLessThanOrEqualTo(few + ALLOWED_GROWTH);
        assertThat(lastResult[0].items()).hasSize(MANY);
    }

    @Test
    @DisplayName("myList: số câu SQL không tăng theo số giao dịch của chính mình")
    void myList_SqlCountDoesNotGrowWithTransactionCount() {
        createExpenses(ownerId, FEW);
        long few = sqlOf(() -> gTransactionService.myList(ownerId, groupId, firstPage(100)));

        createExpenses(ownerId, MANY - FEW);
        long many = sqlOf(() -> gTransactionService.myList(ownerId, groupId, firstPage(100)));

        assertThat(many).isLessThanOrEqualTo(few + ALLOWED_GROWTH);
    }

    @Test
    @DisplayName("listPending: số câu SQL không tăng theo số giao dịch chờ duyệt (thành viên thường tạo)")
    void listPending_SqlCountDoesNotGrowWithTransactionCount() {
        createExpenses(memberId, FEW);
        long few = sqlOf(() -> gTransactionService.listPending(ownerId, groupId, 1, 100));

        createExpenses(memberId, MANY - FEW);
        long many = sqlOf(() -> gTransactionService.listPending(ownerId, groupId, 1, 100));

        assertThat(many).isLessThanOrEqualTo(few + ALLOWED_GROWTH);
    }

    // gọi một lần cho nguội rồi đo lần hai, để không tính chi phí khởi động lần đầu
    private long sqlOf(Supplier<Object> action) {
        action.get();
        long before = SqlCountingConfig.sqlCount();
        action.get();
        return SqlCountingConfig.sqlCount() - before;
    }

    private long sqlOf(Runnable action) {
        return sqlOf(() -> {
            action.run();
            return null;
        });
    }

    private GroupTransactionFilterReq firstPage(int size) {
        return new GroupTransactionFilterReq(null, null, null, null, null, null, null, null, 1, size);
    }

    // chủ nhóm tạo thì giao dịch được duyệt ngay, thành viên thường tạo thì chờ duyệt
    private void createExpenses(UUID operator, int count) {
        for (int i = 0; i < count; i++)
            gTransactionService.create(operator, groupId, new GroupTransactionCreateReq(
                    GTransactionType.EXPENSE, MoneySource.PERSONAL, 10_000L + i, null,
                    LocalDate.now(VIETNAM_ZONE), categoryId, operator, "Chi đếm SQL", null));
    }

    private UUID createUser(Role userRole) {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("count.sql." + UUID.randomUUID() + "@example.com")
                .firstName("Đếm")
                .lastName("SQL")
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build());
        UserRole link = new UserRole(user.getId(), userRole.getId());
        link.setUser(user);
        link.setRole(userRole);
        userRoleRepository.save(link);
        return user.getId();
    }
}
