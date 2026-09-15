package com.datn.financeapp.goal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.goal.entity.SavingsGoal;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
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
class SavingsGoalRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1nb2FsLXJlcG8tdGVzdC0zMmI=");
    }

    @Autowired
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("goal.user@example.com")
                .password("hash")
                .firstName("Goal")
                .lastName("Owner")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        userId = user.getId();

        User otherUser = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("other.goal@example.com")
                .password("hash")
                .firstName("Other")
                .lastName("User")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        otherUserId = otherUser.getId();
    }

    private SavingsGoal createGoal(UUID ownerId, String name, long target, String status) {
        SavingsGoal goal = SavingsGoal.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .name(name)
                .targetAmount(target)
                .savedAmount(0L)
                .targetDate(LocalDate.now().plusMonths(6))
                .status(status)
                .createdAt(Instant.now())
                .build();
        return savingsGoalRepository.saveAndFlush(goal);
    }

    @Test
    @DisplayName("findByIdAndUserId: Chỉ trả về mục tiêu thuộc quyền sở hữu của user (D-27 isolation)")
    void findByIdAndUserId_EnforcesOwnership() {
        SavingsGoal goal = createGoal(userId, "New House", 500_000_000L, "in_progress");

        Optional<SavingsGoal> found = savingsGoalRepository.findByIdAndUserId(goal.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(goal.getId());

        // User khác không thấy (D-27 -> 404)
        assertThat(savingsGoalRepository.findByIdAndUserId(goal.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("findAllForUser: Lọc theo status và sắp xếp theo created_at DESC")
    void findAllForUser_FiltersAndSorts() {
        SavingsGoal g1 = createGoal(userId, "Car", 200_000_000L, "in_progress");
        createGoal(userId, "Phone", 20_000_000L, "completed");

        List<SavingsGoal> activeGoals = savingsGoalRepository.findAllForUser(userId, "in_progress");
        assertThat(activeGoals).extracting(SavingsGoal::getId).containsExactly(g1.getId());
    }

    @Test
    @DisplayName("findSavedAmountNative & findStatusNative: Đọc scalar trực tiếp từ CSDL")
    void findSavedAmountAndStatusNative_ReadsScalarsDirectly() {
        SavingsGoal goal = createGoal(userId, "Trip", 30_000_000L, "in_progress");

        Optional<Long> saved = savingsGoalRepository.findSavedAmountNative(goal.getId());
        Optional<String> status = savingsGoalRepository.findStatusNative(goal.getId());

        assertThat(saved).contains(0L);
        assertThat(status).contains("in_progress");
    }
}
