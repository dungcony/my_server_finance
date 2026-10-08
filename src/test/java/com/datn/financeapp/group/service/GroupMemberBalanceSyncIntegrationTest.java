package com.datn.financeapp.group.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.entity.MemberBalance;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.MemberBalanceRepository;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * Bảng tổng hợp {@code group_member_balances} phải luôn khớp với câu SQL cộng lại từ toàn bộ lịch sử giao dịch
 * ({@code aggregateMemberBalancesByGroupId}) sau mọi thao tác ghi đi qua service. Chạy một chuỗi thao tác cố định
 * để khi lệch thì lần nào cũng tái hiện được đúng bước. Không dùng {@code @Transactional} ở lớp test vì mỗi thao tác
 * phải commit thật như khi gọi từ API.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class GroupMemberBalanceSyncIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ncm91cC1iYWxhbmNlLXN5bmMtdGVzdA==");
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
    private GroupTransactionRepository transactionRepository;

    @Autowired
    private MemberBalanceRepository memberBalanceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID groupId;
    private UUID ownerId;
    private UUID memberA;
    private UUID memberB;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE group_member_balances, group_transaction_participants, group_transactions,"
                + " group_members, group_funds, groups CASCADE");

        ownerId = createUser("owner");
        memberA = createUser("a");
        memberB = createUser("b");
        groupId = createGroupWithFund(ownerId, memberA, memberB);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = 'expense' LIMIT 1", UUID.class);
    }

    @Test
    @DisplayName("Sau mỗi thao tác tạo, duyệt, duyệt hàng loạt, sửa, hoàn tiền, xoá: bảng tổng hợp khớp với lịch sử giao dịch")
    void memberBalances_matchAggregateAfterEveryWrite() {
        // chủ nhóm ghi A nộp quỹ, được duyệt ngay
        transactionService.create(ownerId, groupId, contribution(memberA, 300_000L));
        assertSummaryMatchesHistory("chủ nhóm ghi A nộp 300k");

        // A tự chi tiền túi chia cho A, B nên chờ duyệt, chủ nhóm duyệt
        UUID expenseId = transactionService.create(memberA, groupId, new GroupTransactionCreateReq(
                GTransactionType.EXPENSE, MoneySource.PERSONAL, 90_000L, null, null, categoryId, memberA,
                "Ăn tối", List.of(part(memberA, 45_000L), part(memberB, 45_000L)))).id();
        assertSummaryMatchesHistory("A chi tiền túi 90k (chờ duyệt)");
        transactionService.confirm(ownerId, groupId, expenseId);
        assertSummaryMatchesHistory("chủ nhóm duyệt khoản chi của A");

        // B tự nộp quỹ nên chờ duyệt, chủ nhóm duyệt hàng loạt
        UUID contributionB = transactionService.create(memberB, groupId, contribution(memberB, 100_000L)).id();
        transactionService.bulkConfirm(ownerId, groupId, new GroupTransactionBulkReviewReq(List.of(contributionB)));
        assertSummaryMatchesHistory("chủ nhóm duyệt hàng loạt khoản nộp của B");

        // chủ nhóm sửa khoản chi: đổi số tiền, người tham gia và nguồn tiền
        transactionService.update(ownerId, groupId, expenseId, new GroupTransactionUpdateReq(
                120_000L, null, null, MoneySource.FUND, null, null, null,
                List.of(part(memberA, 40_000L), part(memberB, 40_000L), part(ownerId, 40_000L))));
        assertSummaryMatchesHistory("chủ nhóm sửa khoản chi thành 120k chia 3 người, nguồn quỹ");

        // chủ nhóm hoàn tiền cho A rồi sửa sang người nhận khác
        UUID refundId = transactionService.create(ownerId, groupId, new GroupTransactionCreateReq(
                GTransactionType.REFUND, MoneySource.FUND, 50_000L, null, null, null, memberA,
                "Trả lại tiền", List.of())).id();
        assertSummaryMatchesHistory("chủ nhóm hoàn A 50k");
        transactionService.update(ownerId, groupId, refundId, new GroupTransactionUpdateReq(
                30_000L, null, null, null, null, memberB, null, null));
        assertSummaryMatchesHistory("chủ nhóm sửa khoản hoàn sang B 30k");

        // chủ nhóm xoá khoản nộp của B
        transactionService.delete(ownerId, groupId, contributionB);
        assertSummaryMatchesHistory("chủ nhóm xoá khoản nộp của B");
    }

    // so từng người, từng cột; bỏ các dòng toàn số 0 vì bảng tổng hợp giữ lại dòng đã về 0 còn câu SQL thì không
    private void assertSummaryMatchesHistory(String step) {
        Map<UUID, List<Long>> summary = new HashMap<>();
        for (MemberBalance row : memberBalanceRepository.findByGroupId(groupId))
            putIfNotZero(summary, row.getUserId(),
                    List.of(row.getPaidOutOfPocket(), row.getContribution(), row.getRefund(), row.getShare()));

        Map<UUID, List<Long>> history = new HashMap<>();
        for (GroupTransactionRepository.MemberBalanceProjection p
                : transactionRepository.aggregateMemberBalancesByGroupId(groupId))
            putIfNotZero(history, p.getUserId(),
                    List.of(p.getPaidOutOfPocket(), p.getContribution(), p.getRefund(), p.getShare()));

        assertThat(summary).as("bảng tổng hợp sau bước: " + step).isEqualTo(history);
    }

    private void putIfNotZero(Map<UUID, List<Long>> target, UUID userId, List<Long> values) {
        if (values.stream().anyMatch(v -> v != 0L))
            target.put(userId, values);
    }

    private GroupTransactionCreateReq contribution(UUID transactorId, long amount) {
        return new GroupTransactionCreateReq(GTransactionType.CONTRIBUTION, MoneySource.PERSONAL, amount, null,
                null, null, transactorId, "Nộp quỹ", List.of());
    }

    private GroupTransactionParticipantReq part(UUID userId, long amount) {
        return new GroupTransactionParticipantReq(userId, amount);
    }

    private UUID createGroupWithFund(UUID owner, UUID... members) {
        Group group = groupRepository.save(Group.builder()
                .id(UUID.randomUUID())
                .name("Nhóm kiểm thử số dư")
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
                .email("balance-sync-" + label + "-" + id + "@example.com")
                .password("hashed_password")
                .firstName("Kiểm thử")
                .lastName(label)
                .status(UserStatus.ACTIVE)
                .plan(UserPlan.FREE)
                .createdAt(Instant.now())
                .build()).getId();
    }
}
