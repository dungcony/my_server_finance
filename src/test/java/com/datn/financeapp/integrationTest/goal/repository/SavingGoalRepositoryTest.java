package com.datn.financeapp.integrationTest.goal.repository;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.goal.entity.SavingGoal;
import com.datn.financeapp.module.goal.enums.SavingGoalStatus;
import com.datn.financeapp.module.goal.repository.SavingsGoalRepository;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.UserRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class SavingGoalRepositoryTest {

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
                .build());
        otherUserId = otherUser.getId();
    }

    private SavingGoal createGoal(UUID ownerId, String name, long target, SavingGoalStatus status) {
        SavingGoal goal = SavingGoal.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .name(name)
                .targetAmount(target)
                .savedAmount(0L)
                .targetDate(LocalDate.now().plusMonths(6))
                .status(status)
                .build();
        return savingsGoalRepository.saveAndFlush(goal);
    }

    @Test
    @DisplayName("findByIdAndUserId: Chỉ trả về mục tiêu thuộc quyền sở hữu của user (D-27 isolation)")
    void findByIdAndUserId_EnforcesOwnership() {
        SavingGoal goal = createGoal(userId, "New House", 500_000_000L, SavingGoalStatus.IN_PROGRESS);

        Optional<SavingGoal> found = savingsGoalRepository.findByIdAndUserId(goal.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(goal.getId());

        // User khác không thấy (D-27 -> 404)
        assertThat(savingsGoalRepository.findByIdAndUserId(goal.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("findAllForUser: Lọc theo status và sắp xếp theo created_at DESC")
    void findAllForUser_FiltersAndSorts() {
        SavingGoal g1 = createGoal(userId, "Car", 200_000_000L, SavingGoalStatus.IN_PROGRESS);
        createGoal(userId, "Phone", 20_000_000L, SavingGoalStatus.COMPLETED);

        List<SavingGoal> activeGoals = savingsGoalRepository.findAllForUser(userId, SavingGoalStatus.IN_PROGRESS.name());
        assertThat(activeGoals).extracting(SavingGoal::getId).containsExactly(g1.getId());
    }

    @Test
    @DisplayName("findSavedAmountNative & findStatusNative: Đọc scalar trực tiếp từ CSDL")
    void findSavedAmountAndStatusNative_ReadsScalarsDirectly() {
        SavingGoal goal = createGoal(userId, "Trip", 30_000_000L, SavingGoalStatus.IN_PROGRESS);

        Optional<Long> saved = savingsGoalRepository.findSavedAmountNative(goal.getId());
        Optional<String> status = savingsGoalRepository.findStatusNative(goal.getId());

        assertThat(saved).contains(0L);
        assertThat(status).contains("IN_PROGRESS");
    }
}
