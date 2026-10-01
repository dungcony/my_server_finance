package com.datn.financeapp.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.entity.Fund;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
class GroupRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ncm91cC1yZXBvLXRlc3QtMzJi");
    }

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FundRepository fundRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User testUser;
    private Group testGroup;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("test.group.repo." + UUID.randomUUID() + "@example.com")
                .password("hashed_password")
                .firstName("Van")
                .lastName("Nguyen")
                .status(UserStatus.ACTIVE)
                .plan(UserPlan.FREE)
                .createdAt(Instant.now())
                .build());

        testGroup = groupRepository.save(Group.builder()
                .id(UUID.randomUUID())
                .name("Nhóm Gia Đình Test")
                .description("Nhóm để test repo")
                .inviteCode("GRP99999")
                .status(GroupStatus.ACTIVE)
                .isSettlementEnabled(true)
                .isJoinWithoutConfirm(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Tìm nhóm theo mã mời và loại trừ trạng thái")
    void findByInviteCodeAndStatusNot_success() {
        Optional<Group> found = groupRepository.findByInviteCodeAndStatusNot("GRP99999", GroupStatus.DELETED);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Nhóm Gia Đình Test");

        Optional<Group> notFound = groupRepository.findByInviteCodeAndStatusNot("GRP99999", GroupStatus.ACTIVE);
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("Tìm nhóm theo ID và trạng thái")
    void findByIdAndStatus_success() {
        Optional<Group> found = groupRepository.findByIdAndStatus(testGroup.getId(), GroupStatus.ACTIVE);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(testGroup.getId());

        Optional<Group> notFound = groupRepository.findByIdAndStatus(testGroup.getId(), GroupStatus.ARCHIVED);
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("Tìm thông tin phân quyền MemberAuthInfo của thành viên và quỹ")
    void findAuthInfo_success() {
        memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(testGroup.getId())
                .userId(testUser.getId())
                .role(MemberRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        fundRepository.save(Fund.builder()
                .id(UUID.randomUUID())
                .groupId(testGroup.getId())
                .keepperId(testUser.getId())
                .currentBalance(300_000L)
                .createdAt(Instant.now())
                .build());

        Optional<MemberAuthInfo> authInfoOpt = groupRepository.findAuthInfo(testGroup.getId(), testUser.getId());
        assertThat(authInfoOpt).isPresent();
        MemberAuthInfo authInfo = authInfoOpt.get();
        assertThat(authInfo.groupId()).isEqualTo(testGroup.getId());
        assertThat(authInfo.myId()).isEqualTo(testUser.getId());
        assertThat(authInfo.memberRole()).isEqualTo(MemberRole.OWNER);
        assertThat(authInfo.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(authInfo.keepperId()).isEqualTo(testUser.getId());
        assertThat(authInfo.isOwner()).isTrue();
        assertThat(authInfo.isTreasurer()).isTrue();
    }

    @Test
    @DisplayName("Lấy tóm tắt các nhóm ACTIVE mà user tham gia")
    void findSummariesByUserId_success() {
        memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(testGroup.getId())
                .userId(testUser.getId())
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        List<GroupSummaryRes> activeGroups = groupRepository.findSummariesByUserId(testUser.getId());
        assertThat(activeGroups).hasSize(1);
        GroupSummaryRes res = activeGroups.get(0);
        assertThat(res.id()).isEqualTo(testGroup.getId());
        assertThat(res.myRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(res.memberCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Loại bỏ các nhóm có trạng thái DELETED khỏi danh sách tóm tắt")
    void findSummariesByUserId_excludesDeletedGroups() {
        memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(testGroup.getId())
                .userId(testUser.getId())
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        Group deletedGroup = groupRepository.save(Group.builder()
                .id(UUID.randomUUID())
                .name("Nhóm Đã Xóa")
                .status(GroupStatus.DELETED)
                .inviteCode("GRP88888")
                .isSettlementEnabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        memberRepository.save(Member.builder()
                .id(UUID.randomUUID())
                .groupId(deletedGroup.getId())
                .userId(testUser.getId())
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        List<GroupSummaryRes> activeGroups = groupRepository.findSummariesByUserId(testUser.getId());
        assertThat(activeGroups).hasSize(1);
        assertThat(activeGroups.get(0).id()).isEqualTo(testGroup.getId());
    }

    @Test
    @DisplayName("Tìm nhóm chưa bị xoá kèm thông tin quỹ (findNotDeletedWithFundById)")
    void findNotDeletedWithFundById_success() {
        fundRepository.save(Fund.builder()
                .id(UUID.randomUUID())
                .groupId(testGroup.getId())
                .keepperId(testUser.getId())
                .currentBalance(500_000L)
                .createdAt(Instant.now())
                .build());

        entityManager.flush();
        entityManager.clear();

        Optional<Group> groupWithFundOpt = groupRepository.findNotDeletedWithFundById(testGroup.getId());
        assertThat(groupWithFundOpt).isPresent();
        assertThat(groupWithFundOpt.get().getFund()).isNotNull();
        assertThat(groupWithFundOpt.get().getFund().getCurrentBalance()).isEqualTo(500_000L);
    }
}
