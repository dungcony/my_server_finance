package com.datn.financeapp.group.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Nhiều lệnh hoàn tiền cùng lúc cho một người không được làm tổng tiền hoàn vượt số người đó có trong quỹ.
 * Không dùng {@code @Transactional} ở lớp test vì mỗi luồng phải commit transaction riêng thì mới có tranh chấp thật.
 * Mười luồng vì cần ít nhất bốn lệnh cùng qua bước kiểm mới lộ lỗi (4 x 300k > 1.000k), và mười là số kết nối
 * mặc định của Hikari trong profile test nên luồng nào cũng có kết nối riêng.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class GroupRefundConcurrencyIntegrationTest {

    private static final int THREADS = 10;
    private static final long BALANCE = 1_000_000L;
    private static final long REFUND = 300_000L;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ncm91cC1yZWZ1bmQtY29uY3VycmVuY3k=");
    }

    @Autowired
    private GTransactionService transactionService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID groupId;
    private UUID ownerId;
    private UUID memberA;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE group_member_balances, group_transaction_participants, group_transactions,"
                + " group_members, group_funds, groups CASCADE");

        ownerId = createUser("owner");
        memberA = createUser("a");
        groupId = createGroupWithFund(ownerId, memberA);

        // chủ nhóm ghi A nộp quỹ nên được duyệt ngay, số dư ròng của A bằng BALANCE
        transactionService.create(ownerId, groupId, new GroupTransactionCreateReq(GTransactionType.CONTRIBUTION,
                MoneySource.PERSONAL, BALANCE, null, null, null, memberA, "Nộp quỹ", List.of()));
    }

    @RepeatedTest(5)
    @DisplayName("Mười lệnh hoàn 300k cùng lúc cho người có 1.000k: tổng đã hoàn không vượt 1.000k, lệnh thua bị chặn vì vượt số dư")
    void concurrentRefunds_neverExceedMemberBalance() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Throwable>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return callAndCapture(() -> transactionService.create(ownerId, groupId, new GroupTransactionCreateReq(
                        GTransactionType.REFUND, MoneySource.FUND, REFUND, null, null, null, memberA,
                        "Hoàn tiền", List.of())));
            }));
        }

        startLatch.countDown();
        List<Throwable> outcomes = new ArrayList<>();
        for (Future<Throwable> future : futures)
            outcomes.add(future.get());
        executor.shutdown();

        long succeeded = outcomes.stream().filter(t -> t == null).count();
        List<Throwable> failures = outcomes.stream().filter(t -> t != null).toList();

        assertThat(succeeded * REFUND).as("tổng đã hoàn").isLessThanOrEqualTo(BALANCE);
        assertThat(failures).allSatisfy(t -> assertThat(t).isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_TXN_REFUND_EXCEEDS_BALANCE.getCode()));
    }

    private Throwable callAndCapture(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private UUID createGroupWithFund(UUID owner, UUID... members) {
        Group group = groupRepository.save(Group.builder()
                .id(UUID.randomUUID())
                .name("Nhóm kiểm thử hoàn tiền đồng thời")
                .inviteCode(UUID.randomUUID().toString().substring(0, 8))
                .status(GroupStatus.ACTIVE)
                .isSettlementEnabled(true)
                .isJoinWithoutConfirm(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        jdbcTemplate.update("INSERT INTO group_funds (id, group_id, keepper_id, current_balance, created_at)"
                + " VALUES (?, ?, ?, 0, now())", UUID.randomUUID(), group.getId(), owner);
        addMember(group.getId(), owner, MemberRole.OWNER);
        for (UUID member : members)
            addMember(group.getId(), member, MemberRole.MEMBER);
        return group.getId();
    }

    private void addMember(UUID group, UUID userId, MemberRole role) {
        memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(group)
                .userId(userId)
                .role(role)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now().minusSeconds(86_400))
                .build());
    }

    private UUID createUser(String label) {
        UUID id = UUID.randomUUID();
        return userRepository.save(User.builder()
                .id(id)
                .email("refund-concurrency-" + label + "-" + id + "@example.com")
                .password("hashed_password")
                .firstName("Kiểm thử")
                .lastName(label)
                .status(UserStatus.ACTIVE)
                .plan(UserPlan.FREE)
                .createdAt(Instant.now())
                .build()).getId();
    }
}
