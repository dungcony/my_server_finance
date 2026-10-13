package com.datn.financeapp.module.group.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnParticipantReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.entity.Group;
import com.datn.financeapp.module.group.entity.Member;
import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.repository.GroupRepository;
import com.datn.financeapp.module.group.repository.MemberRepository;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.UserRepository;

import java.time.Instant;
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
 * Giao dịch nhóm đã {@code CONFIRMED} không tự ghi vào sổ cá nhân: listener async (sau commit) gửi thông báo
 * {@code GROUP_TXN_RECORD_REQUEST} kèm data để app điền sẵn form, người dùng đồng ý thì app tự gọi API giao dịch
 * cá nhân. Giao dịch {@code PENDING} chỉ báo người duyệt.
 * Không dùng {@code @Transactional} ở lớp test vì thông báo chỉ được gửi sau khi giao dịch nhóm commit thật.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class GroupPersonalSyncIntegrationTest {

    // thông báo gửi async sau commit nên phải chờ tối đa khoảng này
    private static final long WAIT_MILLIS = 5_000L;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ncm91cC1wZXJzb25hbC1zeW5jLXRlc3Q=");
    }

    @Autowired
    private GroupTxnCreateService createService;

    @Autowired
    private GroupTxnGetService transactionService;

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
    private UUID memberB;
    private String categoryCode;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE notifications, group_member_balances, group_transaction_participants,"
                + " group_transactions, group_members, group_funds, groups CASCADE");

        ownerId = createUser("owner");
        memberA = createUser("a");
        memberB = createUser("b");
        groupId = createGroupWithFund(ownerId, memberA, memberB);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE NOT deleted AND code NOT IN ('gop_quy_nhom', 'nhan_hoan_tien_nhom') LIMIT 1",
                UUID.class);
        categoryCode = jdbcTemplate.queryForObject("SELECT code FROM categories WHERE id = ?", String.class, categoryId);
    }

    @Test
    @DisplayName("Chủ nhóm ghi hộ khoản nộp cho member: CONFIRMED ngay, quỹ đổi, member được hỏi ghi sổ, chưa có khoản cá nhân nào")
    void contributionOnBehalf_confirmedAndAsksMemberToRecord() {
        GroupTxnDetailRes created = createService.create(ownerId, groupId, contribution(memberA, 300_000L));

        assertThat(created.status()).isEqualTo(GroupTxnStatus.CONFIRMED);
        assertThat(fundBalance()).isEqualTo(300_000L);

        Map<String, Object> notice = awaitNotifications(memberA, "GROUP_TXN_RECORD_REQUEST", 1).get(0);
        assertThat(notice.get("reference_id")).isEqualTo(created.id());
        assertThat(notice.get("action")).isEqualTo("RECORD_PERSONAL");
        assertThat(notice.get("group_id")).isEqualTo(groupId.toString());
        assertThat(notice.get("flow")).isEqualTo("EXPENSE");
        assertThat(notice.get("amount")).isEqualTo("300000");
        assertThat(notice.get("display_name")).isEqualTo("Góp quỹ nhóm");
        assertThat(personalTransactionCount(memberA)).isZero();
    }

    @Test
    @DisplayName("Member tự nộp quỹ: PENDING chỉ báo chủ nhóm duyệt; duyệt xong thì member được hỏi ghi sổ")
    void memberContribution_reviewerNotified_thenMemberAsked() {
        GroupTxnDetailRes created = createService.create(memberA, groupId, contribution(memberA, 300_000L));

        assertThat(created.status()).isEqualTo(GroupTxnStatus.PENDING);
        Map<String, Object> reviewNotice = awaitNotifications(ownerId, "GROUP_TXN_REVIEW_REQUEST", 1).get(0);
        assertThat(reviewNotice.get("reference_id")).isEqualTo(created.id());
        assertThat(reviewNotice.get("action")).isEqualTo("REVIEW_TRANSACTION");
        assertThat(notificationCount(memberA, "GROUP_TXN_RECORD_REQUEST")).isZero();

        transactionService.confirm(ownerId, groupId, created.id());

        Map<String, Object> recordNotice = awaitNotifications(memberA, "GROUP_TXN_RECORD_REQUEST", 1).get(0);
        assertThat(recordNotice.get("reference_id")).isEqualTo(created.id());
        assertThat(recordNotice.get("amount")).isEqualTo("300000");
        assertThat(personalTransactionCount(memberA)).isZero();
    }

    @Test
    @DisplayName("Thủ quỹ hoàn tiền cho hai người: không có người chi, mỗi người một thông báo với đúng phần của mình")
    void refundToTwoMembers_eachReceivesTheirShare() {
        createService.create(ownerId, groupId, contribution(memberA, 300_000L));
        createService.create(ownerId, groupId, contribution(memberB, 200_000L));

        GroupTxnDetailRes refund = createService.create(ownerId, groupId, new GroupTxnCreateReq(
                GroupTxnType.REFUND, MoneySource.FUND, 150_000L, Instant.now().minusSeconds(60), null, null,
                "Hoàn quỹ", List.of(
                new GroupTxnParticipantReq(memberA, 100_000L),
                new GroupTxnParticipantReq(memberB, 50_000L))));

        assertThat(refund.status()).isEqualTo(GroupTxnStatus.CONFIRMED);
        assertThat(refund.transactorId()).isNull();
        assertThat(fundBalance()).isEqualTo(350_000L);

        Map<String, Object> noticeA = awaitNotifications(memberA, "GROUP_TXN_RECORD_REQUEST", 2).get(1);
        assertThat(noticeA.get("reference_id")).isEqualTo(refund.id());
        assertThat(noticeA.get("flow")).isEqualTo("INCOME");
        assertThat(noticeA.get("amount")).isEqualTo("100000");
        assertThat(noticeA.get("display_name")).isEqualTo("Nhận hoàn tiền từ nhóm");

        Map<String, Object> noticeB = awaitNotifications(memberB, "GROUP_TXN_RECORD_REQUEST", 2).get(1);
        assertThat(noticeB.get("amount")).isEqualTo("50000");
        assertThat(personalTransactionCount(memberA)).isZero();
        assertThat(personalTransactionCount(memberB)).isZero();
    }

    @Test
    @DisplayName("Member chi tiền quỹ được duyệt: không ai được hỏi ghi sổ")
    void fundExpense_confirmed_asksNobody() {
        GroupTxnDetailRes created = createService.create(memberA, groupId, new GroupTxnCreateReq(
                GroupTxnType.EXPENSE, MoneySource.FUND, 90_000L, Instant.now().minusSeconds(60), categoryCode,
                null, "Ăn tối", List.of(new GroupTxnParticipantReq(memberA, 90_000L))));
        // chờ thông báo duyệt đến nơi để chắc listener async đã chạy xong cho giao dịch này
        awaitNotifications(ownerId, "GROUP_TXN_REVIEW_REQUEST", 1);

        transactionService.confirm(ownerId, groupId, created.id());
        sleepQuietly(500L);

        assertThat(notificationCount(memberA, "GROUP_TXN_RECORD_REQUEST")).isZero();
        assertThat(notificationCount(ownerId, "GROUP_TXN_RECORD_REQUEST")).isZero();
    }

    // ------------------------------- helper -------------------------------

    // chờ đủ số thông báo của user (listener chạy async), sắp theo thời gian tạo
    private List<Map<String, Object>> awaitNotifications(UUID userId, String type, int expected) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        List<Map<String, Object>> rows = notifications(userId, type);
        while (rows.size() < expected && System.currentTimeMillis() < deadline) {
            sleepQuietly(100L);
            rows = notifications(userId, type);
        }
        assertThat(rows).hasSizeGreaterThanOrEqualTo(expected);
        return rows;
    }

    private List<Map<String, Object>> notifications(UUID userId, String type) {
        return jdbcTemplate.queryForList(
                "SELECT reference_id, data ->> 'action' AS action, data ->> 'group_id' AS group_id,"
                        + " data ->> 'type' AS flow, data ->> 'amount' AS amount,"
                        + " data ->> 'display_name' AS display_name"
                        + " FROM notifications WHERE user_id = ? AND type = ? ORDER BY created_at",
                userId, type);
    }

    private long notificationCount(UUID userId, String type) {
        return notifications(userId, type).size();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long fundBalance() {
        Long balance = jdbcTemplate.queryForObject(
                "SELECT current_balance FROM group_funds WHERE group_id = ?", Long.class, groupId);
        return balance == null ? 0 : balance;
    }

    private long personalTransactionCount(UUID userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    // nộp quỹ cho transactorId, người nộp cũng là người duy nhất trong danh sách tham gia
    private GroupTxnCreateReq contribution(UUID transactorId, long amount) {
        return new GroupTxnCreateReq(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, amount,
                Instant.now().minusSeconds(60), null, transactorId, "Nộp quỹ",
                List.of(new GroupTxnParticipantReq(transactorId, amount)));
    }

    private UUID createGroupWithFund(UUID owner, UUID... members) {
        Group group = groupRepository.save(Group.builder()
                .id(UUID.randomUUID())
                .name("Nhóm kiểm thử thông báo ghi sổ cá nhân")
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
                .email("personal-sync-" + label + "-" + id + "@example.com")
                .password("hashed_password")
                .firstName("Kiểm thử")
                .lastName(label)
                .status(UserStatus.ACTIVE)
                .plan(UserPlan.FREE)
                .createdAt(Instant.now())
                .build()).getId();
    }
}
