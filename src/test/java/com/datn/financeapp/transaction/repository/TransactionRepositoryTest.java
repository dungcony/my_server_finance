package com.datn.financeapp.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.transaction.entity.Transaction;
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
class TransactionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci10cmFuc2FjdGlvbi1yZXBvLTM=");
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private UUID userId;
    private UUID otherUserId;
    private UUID walletId;
    private UUID expenseCategoryId;
    private UUID incomeCategoryId;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("txn.user@example.com")
                .password("hash")
                .firstName("Txn")
                .lastName("User")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        userId = user.getId();

        User otherUser = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("other.txn@example.com")
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
                .name("Main Wallet")
                .type("cash")
                .initialBalance(1_000_000L)
                .currentBalance(1_000_000L)
                .includeInTotal(true)
                .sortOrder(0)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        walletId = wallet.getId();

        Category expCat = categoryRepository.findSystemCategoryByName("Cho vay", "expense").orElseThrow();
        expenseCategoryId = expCat.getId();

        Category incCat = categoryRepository.findSystemCategoryByName("Đi vay", "income").orElseThrow();
        incomeCategoryId = incCat.getId();
    }

    private Transaction createTransaction(UUID ownerId, String type, long amount, LocalDate date, String desc, boolean deleted) {
        UUID catId = "income".equalsIgnoreCase(type) ? incomeCategoryId : expenseCategoryId;
        Transaction txn = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .walletId(walletId)
                .categoryId(catId)
                .type(type)
                .amount(amount)
                .date(date)
                .displayName(desc)
                .note("Test note")
                .source("manual")
                .isDeleted(deleted)
                .countsInReport(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return transactionRepository.saveAndFlush(txn);
    }

    @Test
    @DisplayName("findByIdAndUserIdAndIsDeletedFalse: Phân lập dữ liệu theo user và lọc xóa mềm")
    void findByIdAndUserIdAndIsDeletedFalse_EnforcesIsolation() {
        Transaction active = createTransaction(userId, "expense", 50_000, LocalDate.now(), "Coffee", false);
        Transaction deleted = createTransaction(userId, "expense", 20_000, LocalDate.now(), "Snack", true);

        // Chủ sở hữu thấy
        Optional<Transaction> found = transactionRepository.findByIdAndUserIdAndIsDeletedFalse(active.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(active.getId());

        // User khác không thấy (D-27 -> 404)
        assertThat(transactionRepository.findByIdAndUserIdAndIsDeletedFalse(active.getId(), otherUserId)).isEmpty();

        // Xóa mềm không thấy
        assertThat(transactionRepository.findByIdAndUserIdAndIsDeletedFalse(deleted.getId(), userId)).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId: Tìm thấy giao dịch kể cả đã xóa mềm (dùng cho xóa idempotent)")
    void findByIdAndUserId_ReturnsDeleted() {
        Transaction deleted = createTransaction(userId, "expense", 20_000, LocalDate.now(), "Snack", true);

        Optional<Transaction> found = transactionRepository.findByIdAndUserId(deleted.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(deleted.getId());
        assertThat(transactionRepository.findByIdAndUserId(deleted.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("search & countSearch: Lọc giao dịch theo khoảng ngày, loại và từ khóa tìm kiếm")
    void searchAndCountSearch_FiltersAccurately() {
        createTransaction(userId, "expense", 100_000, LocalDate.now(), "Grocery shopping", false);
        createTransaction(userId, "income", 500_000, LocalDate.now(), "Salary bonus", false);
        createTransaction(userId, "expense", 30_000, LocalDate.now().minusDays(10), "Old coffee", false);
        createTransaction(userId, "expense", 40_000, LocalDate.now(), "Deleted expense", true);

        // Tìm kiếm theo từ khóa "shopping"
        List<Transaction> results = transactionRepository.search(
                userId, null, null, null, null, null, null, null, "shopping", null, null, true, "date", "desc", 10, 0);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDisplayName()).isEqualTo("Grocery shopping");

        // Đếm theo type = expense trong ngày hôm nay
        long count = transactionRepository.countSearch(
                userId, LocalDate.now(), LocalDate.now(), "expense", null, null, null, null, null, null, null, false);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("summary: Tính tổng thu và chi, loại bỏ các giao dịch transfer")
    void summary_CalculatesIncomeAndExpense() {
        createTransaction(userId, "income", 1_000_000, LocalDate.now(), "Salary", false);
        createTransaction(userId, "expense", 200_000, LocalDate.now(), "Bills", false);
        createTransaction(userId, "expense", 100_000, LocalDate.now(), "Groceries", false);

        TransactionRepository.SummaryProjection summary = transactionRepository.summary(
                userId, null, null, null, null, null, null, null, null, null, null);

        assertThat(summary.getTotalIncome()).isEqualTo(1_000_000L);
        assertThat(summary.getTotalExpense()).isEqualTo(300_000L);
    }
}
