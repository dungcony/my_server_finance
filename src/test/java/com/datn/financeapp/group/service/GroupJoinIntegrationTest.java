package com.datn.financeapp.group.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
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
 * Tham gia nhóm bằng mã mời trên CSDL thật. Không dùng {@code @Transactional} ở lớp test vì ca đồng
 * thời cần mỗi luồng commit transaction riêng, và ràng buộc {@code uq_group_member_current}
 * (V18, unique một phần trên PENDING/ACTIVE) chỉ nổ ra lúc commit.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class GroupJoinIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ncm91cC1qb2luLWludGVncmF0aW9u");
    }

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Group group;
    private UUID joinerId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM group_members");
        jdbcTemplate.update("DELETE FROM group_funds");
        jdbcTemplate.update("DELETE FROM groups");

        UUID ownerId = createUser("owner").getId();
        joinerId = createUser("joiner").getId();

        group = groupRepository.save(Group.builder()
                .id(UUID.randomUUID())
                .name("Nhóm kiểm thử tham gia")
                .inviteCode(UUID.randomUUID().toString().substring(0, 8))
                .status(GroupStatus.ACTIVE)
                .isSettlementEnabled(true)
                .isJoinWithoutConfirm(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(group.getId())
                .userId(ownerId)
                .role(MemberRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Người từng rời nhóm (dòng LEFT) dùng lại mã mời thì vào lại được, dòng cũ giữ làm lịch sử")
    void join_afterLeaving_succeedsAndKeepsHistory() {
        memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(group.getId())
                .userId(joinerId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.LEFT)
                .joinedAt(Instant.now().minusSeconds(3600))
                .leftAt(Instant.now().minusSeconds(60))
                .build());

        groupService.joinByCode(joinerId, new GroupJoinReq(group.getInviteCode()));

        assertThat(countRows(MemberStatus.ACTIVE)).isEqualTo(1);
        assertThat(countRows(MemberStatus.LEFT)).isEqualTo(1);
    }

    @Test
    @DisplayName("Người đã ACTIVE gọi tham gia lần nữa thì nhận GROUP_MEMBER_ALREADY_EXISTS và không sinh thêm dòng")
    void join_alreadyActive_throwsAlreadyInGroup() {
        groupService.joinByCode(joinerId, new GroupJoinReq(group.getInviteCode()));

        Throwable failure = callAndCapture(() ->
                groupService.joinByCode(joinerId, new GroupJoinReq(group.getInviteCode())));

        assertThat(failure).isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_MEMBER_ALREADY_EXISTS.getCode());
        assertThat(countRows(MemberStatus.ACTIVE)).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("Hai request tham gia cùng lúc của một người: chỉ một dòng ACTIVE, request thua nhận GROUP_MEMBER_ALREADY_EXISTS chứ không phải lỗi 500")
    void join_twoConcurrentRequests_oneWinsOtherGetsAlreadyInGroup() throws Exception {
        String inviteCode = group.getInviteCode();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Throwable>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return callAndCapture(() ->
                        groupService.joinByCode(joinerId, new GroupJoinReq(inviteCode)));
            }));
        }

        startLatch.countDown();
        List<Throwable> outcomes = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            outcomes.add(future.get());
        }
        executor.shutdown();

        List<Throwable> failures = outcomes.stream().filter(t -> t != null).toList();

        assertThat(countRows(MemberStatus.ACTIVE)).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_MEMBER_ALREADY_EXISTS.getCode());
    }

    // gọi một thao tác và trả về lỗi nếu có, null nếu thành công
    private Throwable callAndCapture(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private int countRows(MemberStatus status) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ? AND status = ?",
                Integer.class, group.getId(), joinerId, status.name());
        return count == null ? 0 : count;
    }

    private User createUser(String label) {
        UUID id = UUID.randomUUID();
        return userRepository.save(User.builder()
                .id(id)
                .email("group-join-" + label + "-" + id + "@example.com")
                .password("hashed_password")
                .firstName("Kiểm thử")
                .lastName(label)
                .status(UserStatus.ACTIVE)
                .plan(UserPlan.FREE)
                .createdAt(Instant.now())
                .build());
    }
}
