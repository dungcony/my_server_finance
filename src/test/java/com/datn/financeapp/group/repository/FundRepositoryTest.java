package com.datn.financeapp.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.group.entity.Fund;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
class FundRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1mdW5kLXJlcG8tdGVzdC0zMmI=");
    }

    @Autowired
    private FundRepository fundRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    private Group testGroup;
    private Fund testFund;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        User testUser = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("test.fund." + UUID.randomUUID() + "@example.com")
                .password("hashed_password")
                .firstName("Thủ")
                .lastName("Quỹ")
                .status(UserStatus.ACTIVE)
                .plan(UserPlan.FREE)
                .createdAt(Instant.now())
                .build());

        testGroup = groupRepository.save(Group.builder()
                .id(UUID.randomUUID())
                .name("Nhóm Quỹ Kiểm Thử")
                .description("Nhóm để kiểm tra quỹ")
                .inviteCode("FND88888")
                .status(GroupStatus.ACTIVE)
                .isSettlementEnabled(true)
                .isJoinWithoutConfirm(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        testFund = fundRepository.save(Fund.builder()
                .id(UUID.randomUUID())
                .groupId(testGroup.getId())
                .keepperId(testUser.getId())
                .currentBalance(200_000L)
                .createdAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Tìm quỹ theo Group ID")
    void findByGroupId_success() {
        Optional<Fund> found = fundRepository.findByGroupId(testGroup.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(testFund.getId());
        assertThat(found.get().getCurrentBalance()).isEqualTo(200_000L);
    }

    @Test
    @DisplayName("Điều chỉnh số dư quỹ theo Fund ID (adjustBalance)")
    void adjustBalance_success() {
        int updated = fundRepository.adjustBalance(testFund.getId(), 50_000L);
        assertThat(updated).isEqualTo(1);

        entityManager.clear();

        Optional<Fund> reloaded = fundRepository.findById(testFund.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getCurrentBalance()).isEqualTo(250_000L);
    }

    @Test
    @DisplayName("Điều chỉnh số dư quỹ theo Group ID (adjustBalanceByGroupId)")
    void adjustBalanceByGroupId_success() {
        int updated = fundRepository.adjustBalanceByGroupId(testGroup.getId(), -50_000L);
        assertThat(updated).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();
        Optional<Fund> reloaded = fundRepository.findById(testFund.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getCurrentBalance()).isEqualTo(150_000L);
    }

    @Test
    @DisplayName("Tìm quỹ theo Group ID với khóa FOR UPDATE")
    void findByGroupIdForUpdate_success() {
        Optional<Fund> fundOpt = fundRepository.findByGroupIdForUpdate(testGroup.getId());
        assertThat(fundOpt).isPresent();
        assertThat(fundOpt.get().getId()).isEqualTo(testFund.getId());
    }
}
