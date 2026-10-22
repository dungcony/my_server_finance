package com.datn.financeapp.integrationTest.budget.repository;

import com.datn.financeapp.module.budget.repository.*;


import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.helper.BudgetSpent;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.UserRepository;

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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID otherUserId;
    private String categoryCode;
    private String otherCategoryCode;

    @BeforeEach
    void setUp() {
        userId = saveUser("budget.user@example.com", "Budget", "Owner");
        otherUserId = saveUser("other.budget@example.com", "Other", "User");

        List<String> codes = jdbcTemplate.queryForList(
                "SELECT code FROM categories WHERE is_deleted = FALSE ORDER BY code LIMIT 2", String.class);
        categoryCode = codes.isEmpty() ? "food" : codes.get(0);
        otherCategoryCode = codes.size() > 1 ? codes.get(1) : "shopping";
    }

    private UUID saveUser(String email, String firstName, String lastName) {
        return userRepository.saveAndFlush(User.builder()
                        .id(UUID.randomUUID())
                        .email(email)
                        .password("hash")
                        .firstName(firstName)
                        .lastName(lastName)
                        .plan(UserPlan.FREE)
                        .status(UserStatus.ACTIVE)
                        .build())
                .getId();
    }

    private LocalDate dateOf(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private Budget saveBudget(UUID ownerId, String category, long limit, PeriodType period,
                              LocalDate start, LocalDate end, boolean autoRenew, boolean active) {
        return budgetRepository.saveAndFlush(Budget.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .categoryCode(category)
                .spent(0L)
                .limitAmount(limit)
                .periodType(period)
                .startDate(start)
                .endDate(end)
                .autoRenew(autoRenew)
                .isActive(active)
                .build());
    }

    @Test
    @DisplayName("findByIdAndUserId: chỉ trả ngân sách của chính chủ, người khác nhận rỗng (→ 404)")
    void findByIdAndUserId_enforcesOwnership() {
        Budget b = saveBudget(userId, categoryCode, 5_000_000, PeriodType.MONTH,
                dateOf(2026, 1, 1), dateOf(2026, 1, 31), false, true);

        Optional<Budget> found = budgetRepository.findByIdAndUserId(b.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(b.getId());

        assertThat(budgetRepository.findByIdAndUserId(b.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("search: lọc theo hiệu lực và loại kỳ, sắp theo ngày bắt đầu mới nhất trước")
    void search_filtersAndSorts() {
        Budget feb = saveBudget(userId, categoryCode, 5_000_000, PeriodType.MONTH,
                dateOf(2026, 2, 1), dateOf(2026, 2, 28), false, true);
        Budget jan = saveBudget(userId, categoryCode, 10_000_000, PeriodType.MONTH,
                dateOf(2026, 1, 1), dateOf(2026, 1, 31), false, true);
        saveBudget(userId, categoryCode, 1_000_000, PeriodType.WEEK,
                dateOf(2026, 1, 1), dateOf(2026, 1, 7), false, false);

        List<Budget> list = budgetRepository.search(userId, true, PeriodType.MONTH);

        assertThat(list).extracting(Budget::getId).containsExactly(feb.getId(), jan.getId());
    }

    @Test
    @DisplayName("search: không truyền bộ lọc thì lấy cả ngân sách đã tắt, và không lẫn của người khác")
    void search_noFilters_returnsOnlyOwn() {
        Budget mine = saveBudget(userId, categoryCode, 1_000_000, PeriodType.MONTH,
                dateOf(2026, 1, 1), dateOf(2026, 1, 31), false, false);
        saveBudget(otherUserId, categoryCode, 1_000_000, PeriodType.MONTH,
                dateOf(2026, 1, 1), dateOf(2026, 1, 31), false, true);

        List<Budget> list = budgetRepository.search(userId, null, null);

        assertThat(list).extracting(Budget::getId).containsExactly(mine.getId());
    }

    @Test
    @DisplayName("findExpiredAutoRenewIds: chỉ lấy ngân sách đang hiệu lực, tự gia hạn và đã hết kỳ")
    void findExpiredAutoRenewIds_findsOnlyExpiredAutoRenew() {
        Budget expired = saveBudget(userId, categoryCode, 3_000_000, PeriodType.MONTH,
                dateOf(2026, 1, 1), dateOf(2026, 1, 31), true, true);
        saveBudget(userId, otherCategoryCode, 3_000_000, PeriodType.MONTH,
                dateOf(2026, 1, 1), dateOf(2026, 1, 31), false, true);

        List<UUID> ids = budgetRepository.findExpiredAutoRenewIds(dateOf(2026, 2, 15));

        assertThat(ids).containsExactly(expired.getId());
    }

    @Test
    @DisplayName("existsOverlapping: phát hiện trùng danh mục trong kỳ chồng lấn, kỳ khác thì không")
    void existsOverlapping_detectsSameDates() {
        LocalDate start = dateOf(2026, 3, 1);
        LocalDate end = dateOf(2026, 3, 31);
        Budget existing = saveBudget(userId, categoryCode, 2_000_000, PeriodType.MONTH, start, end, false, true);

        assertThat(budgetRepository.existsOverlapping(userId, categoryCode, start, end)).isTrue();
        assertThat(budgetRepository.existsOverlapping(
                userId, categoryCode, dateOf(2026, 4, 1), dateOf(2026, 4, 30))).isFalse();
        assertThat(budgetRepository.existsOverlapExceptId(
                userId, categoryCode, start, end, existing.getId())).isFalse();
    }

    @Test
    @DisplayName("addSpent: cộng nguyên tử và không bao giờ xuống dưới 0")
    void addSpent_addsAtomicallyAndFloorsAtZero() {
        Budget b = saveBudget(userId, categoryCode, 3_000_000, PeriodType.MONTH,
                dateOf(2026, 3, 1), dateOf(2026, 3, 31), false, true);

        budgetRepository.addSpent(List.of(b.getId()), 400_000L);
        assertThat(budgetRepository.findSpentByIds(List.of(b.getId())).get(0).spent()).isEqualTo(400_000L);

        budgetRepository.addSpent(List.of(b.getId()), -1_000_000L);
        assertThat(budgetRepository.findSpentByIds(List.of(b.getId())).get(0).spent()).isZero();
    }

    @Test
    @DisplayName("findActiveByCategoryCodes: chỉ ngân sách của chính user, đang hiệu lực, có ngày nằm trong kỳ")
    void findActiveByCategoryCodes_filtersByUserActiveAndDate() {
        Budget covering = saveBudget(userId, categoryCode, 3_000_000, PeriodType.MONTH,
                dateOf(2026, 3, 1), dateOf(2026, 3, 31), false, true);
        saveBudget(otherUserId, categoryCode, 3_000_000, PeriodType.MONTH,
                dateOf(2026, 3, 1), dateOf(2026, 3, 31), false, true);
        saveBudget(userId, otherCategoryCode, 3_000_000, PeriodType.MONTH,
                dateOf(2026, 3, 1), dateOf(2026, 3, 31), false, true);

        List<BudgetSpent> views = budgetRepository.findActiveByCategoryCodes(
                userId, List.of(categoryCode), dateOf(2026, 3, 15));

        assertThat(views).extracting(BudgetSpent::id).containsExactly(covering.getId());
        assertThat(budgetRepository.findActiveByCategoryCodes(userId, List.of(categoryCode), dateOf(2026, 4, 1)))
                .isEmpty();
    }
}
