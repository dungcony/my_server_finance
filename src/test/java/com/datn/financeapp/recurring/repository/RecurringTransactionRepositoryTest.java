package com.datn.financeapp.recurring.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.recurring.entity.RecurringTransaction;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
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
class RecurringTransactionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1yZWN1cnJpbmctcmVwby10ZXN0");
    }

    @Autowired
    private RecurringTransactionRepository recurringRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private UUID userId;
    private UUID otherUserId;
    private UUID walletId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("recurring.user@example.com")
                .password("hash")
                .firstName("Recurring")
                .lastName("Owner")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        userId = user.getId();

        User otherUser = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("other.recurring@example.com")
                .password("hash")
                .firstName("Other")
                .lastName("User")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        otherUserId = otherUser.getId();

        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Recurring Wallet")
                .type("cash")
                .initialBalance(1_000_000L)
                .currentBalance(1_000_000L)
                .includeInTotal(true)
                .sortOrder(0)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        walletId = wallet.getId();

        Category cat = categoryRepository.findSystemCategoryByName("Cho vay", "expense").orElseThrow();
        categoryId = cat.getId();
    }

    private RecurringTransaction createRecurring(UUID ownerId, String name, String type, long amount, LocalDate nextRun, boolean enabled) {
        RecurringTransaction rec = RecurringTransaction.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .walletId(walletId)
                .categoryId(categoryId)
                .type(type)
                .amount(amount)
                .displayName(name)
                .frequency("month")
                .interval(1)
                .startDate(LocalDate.now())
                .nextRunDate(nextRun)
                .isEnabled(enabled)
                .createdAt(Instant.now())
                .build();
        return recurringRepository.saveAndFlush(rec);
    }

    @Test
    @DisplayName("findByIdAndUserId: Chỉ trả về khoản định kỳ của chính user (D-27 isolation)")
    void findByIdAndUserId_EnforcesOwnership() {
        RecurringTransaction rec = createRecurring(userId, "Rent", "expense", 5_000_000L, LocalDate.now().plusDays(5), true);

        Optional<RecurringTransaction> found = recurringRepository.findByIdAndUserId(rec.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(rec.getId());

        // User khác không thấy (D-27 -> 404)
        assertThat(recurringRepository.findByIdAndUserId(rec.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("findAllForUser: Lọc theo isEnabled và type, sắp xếp theo next_run_date")
    void findAllForUser_FiltersAndSorts() {
        RecurringTransaction r1 = createRecurring(userId, "Gym", "expense", 500_000L, LocalDate.now().plusDays(2), true);
        RecurringTransaction r2 = createRecurring(userId, "Salary", "income", 20_000_000L, LocalDate.now().plusDays(10), true);
        createRecurring(userId, "Old Sub", "expense", 100_000L, LocalDate.now().plusDays(1), false);

        List<RecurringTransaction> enabledExpenses = recurringRepository.findAllForUser(userId, true, "expense");
        assertThat(enabledExpenses).extracting(RecurringTransaction::getId).containsExactly(r1.getId());
    }

    @Test
    @DisplayName("findDue: Tìm các khoản định kỳ enabled có next_run_date <= hôm nay")
    void findDue_FindsDueItems() {
        RecurringTransaction due = createRecurring(userId, "Due Item", "expense", 200_000L, LocalDate.now().minusDays(1), true);
        createRecurring(userId, "Future Item", "expense", 200_000L, LocalDate.now().plusDays(5), true);
        createRecurring(userId, "Disabled Due", "expense", 200_000L, LocalDate.now().minusDays(1), false);

        List<RecurringTransaction> dueItems = recurringRepository.findDue(LocalDate.now());
        assertThat(dueItems).extracting(RecurringTransaction::getId).contains(due.getId());
    }
}
