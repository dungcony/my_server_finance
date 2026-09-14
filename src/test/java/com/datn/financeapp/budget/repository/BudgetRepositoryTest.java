package com.datn.financeapp.budget.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.budget.entity.Budget;
import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.repository.CategoryRepository;
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
class BudgetRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1idWRnZXQtcmVwby10ZXN0LTMyYg==");
    }

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private UUID userId;
    private UUID otherUserId;
    private UUID expenseCategoryId;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("budget.user@example.com")
                .password("hash")
                .firstName("Budget")
                .lastName("Owner")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        userId = user.getId();

        User otherUser = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("other.budget@example.com")
                .password("hash")
                .firstName("Other")
                .lastName("User")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        otherUserId = otherUser.getId();

        Category cat = categoryRepository.findSystemCategoryByName("Cho vay", "expense").orElseThrow();
        expenseCategoryId = cat.getId();
    }

    private Budget createBudget(UUID ownerId, long limit, String period, LocalDate start, LocalDate end, boolean autoRenew, boolean active) {
        Budget b = Budget.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .categoryId(expenseCategoryId)
                .limitAmount(limit)
                .periodType(period)
                .startDate(start)
                .endDate(end)
                .autoRenew(autoRenew)
                .isActive(active)
                .createdAt(Instant.now())
                .build();
        return budgetRepository.saveAndFlush(b);
    }

    @Test
    @DisplayName("findByIdForUser: Chỉ trả về ngân sách thuộc quyền sở hữu của user (D-27 -> 404)")
    void findByIdForUser_EnforcesOwnership() {
        Budget b = createBudget(userId, 5_000_000, "month", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), false, true);

        Optional<Budget> found = budgetRepository.findByIdForUser(b.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(b.getId());

        assertThat(budgetRepository.findByIdForUser(b.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("findAllForUser: Lọc theo isActive, periodType và sắp xếp theo start_date DESC")
    void findAllForUser_FiltersAndSorts() {
        Budget b1 = createBudget(userId, 5_000_000, "month", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), false, true);
        Budget b2 = createBudget(userId, 10_000_000, "month", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), false, true);
        createBudget(userId, 1_000_000, "week", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), false, false);

        // Lọc active + month
        List<Budget> list = budgetRepository.findAllForUser(userId, true, "month");
        assertThat(list).extracting(Budget::getId).containsExactly(b1.getId(), b2.getId());
    }

    @Test
    @DisplayName("findAutoRenewExpired: Tìm các ngân sách tự gia hạn đã hết hạn")
    void findAutoRenewExpired_FindsExpiredBudgets() {
        Budget expired = createBudget(userId, 3_000_000, "month", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), true, true);
        createBudget(userId, 3_000_000, "month", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), false, true); // autoRenew=false

        List<Budget> expiredList = budgetRepository.findAutoRenewExpired(LocalDate.of(2026, 2, 15));
        assertThat(expiredList).extracting(Budget::getId).contains(expired.getId());
    }

    @Test
    @DisplayName("existsOverlapping: Kiểm tra trùng lặp ngân sách cùng danh mục và khoảng thời gian")
    void existsOverlapping_DetectsSameDates() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        createBudget(userId, 2_000_000, "month", start, end, false, true);

        boolean exists = budgetRepository.existsOverlapping(userId, expenseCategoryId, null, start, end);
        assertThat(exists).isTrue();

        boolean differentDates = budgetRepository.existsOverlapping(
                userId, expenseCategoryId, null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertThat(differentDates).isFalse();
    }
}
