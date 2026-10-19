package com.datn.financeapp.integrationTest.group.service;

import com.datn.financeapp.module.group.service.*;


import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.module.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnParticipantReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnFilterReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnListRes;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import com.datn.financeapp.performance.SqlCountingConfig;
import com.datn.financeapp.module.user.entity.Role;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.entity.UserRole;
import com.datn.financeapp.module.user.enums.RoleName;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.RoleRepository;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.module.user.repository.UserRoleRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chặn N+1 ở các danh sách giao dịch nhóm: số câu SQL của {@code list}, {@code myList}, {@code listPending} không được
 * tăng theo số giao dịch trong trang.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, SqlCountingConfig.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class GroupTransactionListQueryCountIntegrationTest {

    private static final int FEW = 10;
    private static final int MANY = 100;
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
    private GroupTxnGetService groupTxnGetService;

    @Autowired
    private GroupTxnCreateService groupTxnCreateService;

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
    private String categoryCode;

    private void authAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @BeforeEach
    void createGroup() {
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER).orElseThrow();
        ownerId = createUser(userRole);
        memberId = createUser(userRole);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE NOT deleted LIMIT 1", UUID.class);
        categoryCode = jdbcTemplate.queryForObject("SELECT code FROM categories WHERE id = ?", String.class, categoryId);
        
        authAs(ownerId);
        groupId = groupService.create(new GroupCreateReq(
                "Nhóm đếm SQL", "Chặn N+1 danh sách giao dịch", 10_000_000L, true, true, List.of(memberId))).id();
    }

    @Test
    @DisplayName("list: số câu SQL không tăng theo số giao dịch")
    void list_SqlCountDoesNotGrowWithTransactionCount() {
        createExpenses(ownerId, FEW);
        authAs(ownerId);
        Pageable page = PageRequest.of(0, 100);
        long few = sqlOf(() -> groupTxnGetService.generalList(groupId, emptyFilter(), page));

        createExpenses(ownerId, MANY - FEW);
        authAs(ownerId);
        GroupTxnListRes[] lastResult = new GroupTxnListRes[1];
        long many = sqlOf(() -> lastResult[0] = groupTxnGetService.generalList(groupId, emptyFilter(), page));

        assertThat(many).isLessThanOrEqualTo(few + ALLOWED_GROWTH);
        assertThat(lastResult[0].items()).hasSize(MANY);
    }

    @Test
    @DisplayName("myList: số câu SQL không tăng theo số giao dịch của chính mình")
    void myList_SqlCountDoesNotGrowWithTransactionCount() {
        createExpenses(ownerId, FEW);
        authAs(ownerId);
        Pageable page = PageRequest.of(0, 100);
        long few = sqlOf(() -> groupTxnGetService.myList(groupId, null, emptyFilter(), page));

        createExpenses(ownerId, MANY - FEW);
        authAs(ownerId);
        long many = sqlOf(() -> groupTxnGetService.myList(groupId, null, emptyFilter(), page));

        assertThat(many).isLessThanOrEqualTo(few + ALLOWED_GROWTH);
    }

    @Test
    @DisplayName("listPending: số câu SQL không tăng theo số giao dịch chờ duyệt (thành viên thường tạo)")
    void listPending_SqlCountDoesNotGrowWithTransactionCount() {
        createExpenses(memberId, FEW);
        authAs(ownerId);
        Pageable page = PageRequest.of(0, 100);
        long few = sqlOf(() -> groupTxnGetService.all(groupId, List.of(GroupTxnStatus.PENDING), emptyFilter(), page));

        createExpenses(memberId, MANY - FEW);
        authAs(ownerId);
        long many = sqlOf(() -> groupTxnGetService.all(groupId, List.of(GroupTxnStatus.PENDING), emptyFilter(), page));

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

    private GroupTxnFilterReq emptyFilter() {
        return new GroupTxnFilterReq(null, null, null, null, null, null, null, null);
    }

    // chủ nhóm tạo thì giao dịch được duyệt ngay, thành viên thường tạo thì chờ duyệt
    private void createExpenses(UUID operator, int count) {
        authAs(operator);
        for (int i = 0; i < count; i++) {
            long amount = 10_000L + i;
            groupTxnCreateService.create(groupId, new GroupTxnCreateReq(
                    GroupTxnType.EXPENSE, MoneySource.PERSONAL, amount, Instant.now().minusSeconds(60),
                    categoryCode, operator, "Chi đếm SQL",
                    List.of(new GroupTxnParticipantReq(operator, amount))));
        }
    }

    private UUID createUser(Role userRole) {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("count.sql." + UUID.randomUUID() + "@example.com")
                .firstName("Đếm")
                .lastName("SQL")
                .status(UserStatus.ACTIVE)
                .build());
        UserRole link = new UserRole(user.getId(), userRole.getId());
        link.setUser(user);
        link.setRole(userRole);
        userRoleRepository.save(link);
        return user.getId();
    }
}
