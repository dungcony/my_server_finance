package com.datn.financeapp.integrationTest.group.service;

import com.datn.financeapp.module.group.service.*;


import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.group.dto.request.transaction.BatchGroupTxnUpdateStatusReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnParticipantReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnUpdateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnUpdateStatusReq;
import com.datn.financeapp.module.group.entity.Group;
import com.datn.financeapp.module.group.entity.Member;
import com.datn.financeapp.module.group.entity.MemberBalance;
import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.repository.GroupRepository;
import com.datn.financeapp.module.group.repository.MemberBalanceRepository;
import com.datn.financeapp.module.group.repository.MemberRepository;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.UserRepository;

import java.time.Instant;
import java.util.Collections;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bảng tổng hợp {@code group_member_balances} phải luôn khớp với câu SQL cộng lại từ toàn bộ lịch sử giao dịch
 * sau mọi thao tác ghi đi qua service. Chạy một chuỗi thao tác cố định
 * để khi lệch thì lần nào cũng tái hiện được đúng bước. Không dùng {@code @Transactional} ở lớp test vì mỗi thao tác
 * của service phải commit thật để trigger và trigger sau đó nhìn thấy dữ liệu thật trong DB.
 * Chạy riêng: {@code mvn test -Dtest=GroupMemberBalanceSyncIntegrationTest}
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
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1iYWxhbmNlLXN5bmMtdGVzdC0wNjA2MDYwNg==");
    }

    @Autowired
    private GroupTxnCreateService createService;

    @Autowired
    private GroupTxnUpdateService updateService;

    @Autowired
    private GroupTxnDeleteService deleteService;

    @Autowired
    private MemberBalanceRepository memberBalanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID memberA;
    private UUID memberB;
    private UUID groupId;
    private UUID categoryId;
    private String categoryCode;

    @BeforeEach
    void setUpData() {
        ownerId = createUser("sync.owner@example.com");
        memberA = createUser("sync.a@example.com");
        memberB = createUser("sync.b@example.com");
        groupId = createGroupWithFund(ownerId, memberA, memberB);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE NOT deleted LIMIT 1", UUID.class);
        categoryCode = jdbcTemplate.queryForObject("SELECT code FROM categories WHERE id = ?", String.class, categoryId);
    }

    @Test
    @DisplayName("Sau mỗi thao tác tạo, duyệt, duyệt hàng loạt, sửa, hoàn tiền, xoá: bảng tổng hợp khớp với lịch sử giao dịch")
    void memberBalances_matchAggregateAfterEveryWrite() {
        // chủ nhóm ghi A nộp quỹ, được duyệt ngay
        authAs(ownerId);
        createService.create(groupId, contribution(memberA, 300_000L));
        assertSummaryMatchesHistory("chủ nhóm ghi A nộp 300k");

        // A tự chi tiền túi chia cho A, B nên chờ duyệt, chủ nhóm duyệt
        authAs(memberA);
        UUID expenseId = createService.create(groupId, new GroupTxnCreateReq(
                GroupTxnType.EXPENSE, MoneySource.PERSONAL, 90_000L, Instant.now().minusSeconds(60),
                categoryCode, memberA, "Ăn tối", List.of(part(memberA, 45_000L), part(memberB, 45_000L)))).id();
        assertSummaryMatchesHistory("A chi tiền túi 90k (chờ duyệt)");
        authAs(ownerId);
        updateService.status(groupId, new GroupTxnUpdateStatusReq(expenseId, GroupTxnStatus.CONFIRMED, null));
        assertSummaryMatchesHistory("chủ nhóm duyệt khoản chi của A");

        // B tự nộp quỹ nên chờ duyệt, chủ nhóm duyệt hàng loạt
        authAs(memberB);
        UUID contributionB = createService.create(groupId, contribution(memberB, 100_000L)).id();
        authAs(ownerId);
        updateService.statuses(groupId, new BatchGroupTxnUpdateStatusReq(List.of(contributionB), GroupTxnStatus.CONFIRMED));
        assertSummaryMatchesHistory("chủ nhóm duyệt hàng loạt khoản nộp của B");

        // chủ nhóm sửa khoản chi: đổi số tiền, người tham gia và nguồn tiền
        authAs(ownerId);
        updateService.update(groupId, new GroupTxnUpdateReq(
                expenseId, 120_000L, null, null, MoneySource.FUND, null, null, null, null,
                List.of(part(memberA, 40_000L), part(memberB, 40_000L), part(ownerId, 40_000L))));
        assertSummaryMatchesHistory("chủ nhóm sửa khoản chi thành 120k chia 3 người, nguồn quỹ");

        // chủ nhóm (cũng là thủ quỹ) hoàn tiền cho A 50k và B 30k trong cùng một giao dịch
        authAs(ownerId);
        createService.create(groupId, new GroupTxnCreateReq(
                GroupTxnType.REFUND, MoneySource.FUND, 80_000L, Instant.now().minusSeconds(60), null, null,
                "Trả lại tiền", List.of(part(memberA, 50_000L), part(memberB, 30_000L))));
        assertSummaryMatchesHistory("chủ nhóm hoàn A 50k và B 30k");

        // chủ nhóm xoá khoản nộp của B
        authAs(ownerId);
        deleteService.softDelete(groupId, contributionB);
        assertSummaryMatchesHistory("chủ nhóm xoá khoản nộp của B");
    }

    // so từng người, từng cột; bỏ các dòng toàn số 0 vì bảng tổng hợp giữ lại dòng đã về 0 còn câu SQL thì không
    private void assertSummaryMatchesHistory(String step) {
        Map<UUID, List<Long>> summary = new HashMap<>();
        for (MemberBalance row : memberBalanceRepository.findByGroupId(groupId))
            putIfNotZero(summary, row.getUserId(),
                    List.of(row.getContribution(), row.getRefund(), row.getShare()));

        String sql = """
                SELECT
                    user_id,
                    COALESCE(SUM(contribution), 0)       AS contribution,
                    COALESCE(SUM(refund), 0)             AS refund,
                    COALESCE(SUM(share), 0)              AS share
                FROM (
                    SELECT transactor_id AS user_id, amount AS contribution, 0 AS refund, 0 AS share
                    FROM group_transactions
                    WHERE group_id = ? 
                      AND status = 'CONFIRMED' 
                      AND deleted_at IS NULL
                      AND (type = 'CONTRIBUTION' OR (type = 'EXPENSE' AND money_source = 'PERSONAL'))
                
                    UNION ALL
                
                    SELECT transactor_id AS user_id, 0, amount, 0
                    FROM group_transactions
                    WHERE group_id = ? 
                      AND status = 'CONFIRMED' 
                      AND deleted_at IS NULL
                      AND type = 'REFUND'
                
                    UNION ALL
                
                    SELECT 
                        p.user_id, 
                        0, 
                        0, 
                        CASE 
                            WHEN gt.type = 'ADJUSTMENT_UP' THEN -p.share_amount 
                            ELSE p.share_amount 
                        END AS share
                    FROM group_transaction_participants p
                    JOIN group_transactions gt ON gt.id = p.group_transaction_id
                    WHERE gt.group_id = ? 
                      AND gt.status = 'CONFIRMED' 
                      AND gt.deleted_at IS NULL
                ) combined
                GROUP BY user_id
                """;

        Map<UUID, List<Long>> history = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            UUID userId = rs.getObject("user_id", UUID.class);
            putIfNotZero(history, userId, List.of(
                    rs.getLong("contribution"),
                    rs.getLong("refund"),
                    rs.getLong("share")
            ));
        }, groupId, groupId, groupId);

        assertThat(summary).as("bảng tổng hợp sau bước: " + step).isEqualTo(history);
    }

    private void putIfNotZero(Map<UUID, List<Long>> target, UUID userId, List<Long> values) {
        if (values.stream().anyMatch(v -> v != 0L))
            target.put(userId, values);
    }

    private GroupTxnCreateReq contribution(UUID transactorId, long amount) {
        return new GroupTxnCreateReq(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, amount,
                Instant.now().minusSeconds(60), null, transactorId, "Nộp quỹ",
                List.of(part(transactorId, amount)));
    }

    private GroupTxnParticipantReq part(UUID userId, long amount) {
        return new GroupTxnParticipantReq(userId, amount);
    }

    private void authAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList())
        );
    }

    private UUID createGroupWithFund(UUID owner, UUID... members) {
        Group group = groupRepository.save(Group.builder()
                .id(UUID.randomUUID())
                .name("Nhóm kiểm thử số dư")
                .inviteCode(UUID.randomUUID().toString().substring(0, 8))
                .status(GroupStatus.ACTIVE)
                .isSettlementEnabled(true)
                .isJoinWithoutConfirm(true)
                .build());
        jdbcTemplate.update("INSERT INTO group_funds (id, group_id, keepper_id, current_balance, created_at)"
                + " VALUES (?, ?, ?, 0, now())", UUID.randomUUID(), group.getId(), owner);
        addMember(group.getId(), owner, MemberRole.OWNER);
        for (UUID member : members)
            addMember(group.getId(), member, MemberRole.MEMBER);
        return group.getId();
    }

    private void addMember(UUID group, UUID user, MemberRole role) {
        memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(group)
                .userId(user)
                .role(role)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());
    }

    private UUID createUser(String email) {
        return userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .firstName("Sync")
                .lastName("Balance")
                .status(UserStatus.ACTIVE)
                .plan(UserPlan.FREE)
                .build()).getId();
    }
}
