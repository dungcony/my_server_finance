package com.datn.financeapp.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Transactional
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class MemberRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1tZW1iZXItcmVwby10ZXN0");
    }

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser1;
    private User testUser2;
    private Group testGroup;
    private Member testMember1;

    @BeforeEach
    void setUp() {
        testUser1 = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("test.mem1." + UUID.randomUUID() + "@example.com")
                .password("hashed_password")
                .firstName("Thành")
                .lastName("Viên 1")
                .status(UserStatus.ACTIVE)
                .plan(UserPlan.FREE)
                .createdAt(Instant.now())
                .build());

        testUser2 = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("test.mem2." + UUID.randomUUID() + "@example.com")
                .password("hashed_password")
                .firstName("Thành")
                .lastName("Viên 2")
                .status(UserStatus.ACTIVE)
                .plan(UserPlan.FREE)
                .createdAt(Instant.now())
                .build());

        testGroup = groupRepository.save(Group.builder()
                .id(UUID.randomUUID())
                .name("Nhóm Kiểm Tra Thành Viên")
                .inviteCode("MEM12345")
                .status(GroupStatus.ACTIVE)
                .isSettlementEnabled(true)
                .isJoinWithoutConfirm(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        testMember1 = memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(testGroup.getId())
                .userId(testUser1.getId())
                .role(MemberRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build());
    }

    @Test
    @DisplayName("Tìm thành viên theo Group ID và User ID")
    void findByGroupIdAndUserId_success() {
        Optional<Member> found = memberRepository.findByGroupIdAndUserId(testGroup.getId(), testUser1.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(testMember1.getId());
        assertThat(found.get().getRole()).isEqualTo(MemberRole.OWNER);
    }

    @Test
    @DisplayName("Tìm thành viên theo vai trò và trạng thái")
    void findByGroupIdAndRoleAndStatus_success() {
        Optional<Member> ownerOpt = memberRepository.findByGroupIdAndRoleAndStatus(
                testGroup.getId(), MemberRole.OWNER, MemberStatus.ACTIVE
        );
        assertThat(ownerOpt).isPresent();
        assertThat(ownerOpt.get().getUserId()).isEqualTo(testUser1.getId());
    }

    @Test
    @DisplayName("Kiểm tra allMemberInGroup khi tất cả ID đều thuộc nhóm")
    void allMemberInGroup_success() {
        memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(testGroup.getId())
                .userId(testUser2.getId())
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        boolean allIn = memberRepository.allMemberInGroup(testGroup.getId(), Set.of(testUser1.getId(), testUser2.getId()));
        assertThat(allIn).isTrue();

        boolean notAllIn = memberRepository.allMemberInGroup(testGroup.getId(), Set.of(testUser1.getId(), UUID.randomUUID()));
        assertThat(notAllIn).isFalse();
    }

    @Test
    @DisplayName("Tìm danh sách User ID thành viên có mặt tại thời điểm phát sinh giao dịch (findMemberUserIdsAtOccurredAt)")
    void findMemberUserIdsAtOccurredAt_success() {
        Instant occurredAt = Instant.now().minus(1, ChronoUnit.HOURS);

        List<UUID> memberIds = memberRepository.findMemberUserIdsAtOccurredAt(testGroup.getId(), occurredAt);
        assertThat(memberIds).contains(testUser1.getId());
    }

    @Test
    @DisplayName("Cập nhật trạng thái và thời điểm tham gia (updateStatusAndJoinedAt)")
    void updateStatusAndJoinedAt_success() {
        Member pendingMember = memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(testGroup.getId())
                .userId(testUser2.getId())
                .role(MemberRole.MEMBER)
                .status(MemberStatus.PENDING)
                .build());

        Instant now = Instant.now();
        int rows = memberRepository.updateStatusAndJoinedAt(
                testGroup.getId(), testUser2.getId(), MemberStatus.PENDING, MemberStatus.ACTIVE, now
        );
        assertThat(rows).isEqualTo(1);

        Optional<Member> reloaded = memberRepository.findById(pendingMember.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }
}
