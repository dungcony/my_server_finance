package com.datn.financeapp.integrationTest.transaction.repository;

import com.datn.financeapp.module.transaction.repository.*;


import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.category.repository.CategoryRepository;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.datn.financeapp.module.wallet.repository.WalletRepository;

import java.time.Instant;
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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID otherUserId;
    private UUID walletId;
    private String leafCategoryCode;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM transactions");
        walletRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("txn.user@example.com")
                .password("hash")
                .firstName("Txn")
                .lastName("User")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
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
                .build());
        otherUserId = otherUser.getId();

        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Main Wallet")
                .type(WalletType.CASH)
                .balance(1_000_000L)
                .iconCode("default_wallet")
                .color("#112233")
                .isDefault(false)
                .build());
        walletId = wallet.getId();

        leafCategoryCode = jdbcTemplate.queryForObject(
                "SELECT code FROM categories WHERE is_deleted = false AND is_leaf = true LIMIT 1", String.class);
    }

    private Transaction createTransaction(UUID ownerId, TxnType type, long amount, Instant occurredAt, String name, boolean deleted) {
        Transaction txn = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .walletId(walletId)
                .categoryCode(leafCategoryCode)
                .type(type)
                .amount(amount)
                .occurredAt(occurredAt)
                .name(name)
                .note("Test note")
                .build();
        txn.setIsDeleted(deleted);
        return transactionRepository.saveAndFlush(txn);
    }

    @Test
    @DisplayName("findByIdAndUserIdAndIsDeletedFalse: Phân lập dữ liệu theo user và lọc xóa mềm")
    void findByIdAndUserIdAndIsDeletedFalse_EnforcesIsolation() {
        Transaction active = createTransaction(userId, TxnType.EXPENSE, 50_000, Instant.now(), "Coffee", false);
        Transaction deleted = createTransaction(userId, TxnType.EXPENSE, 20_000, Instant.now(), "Snack", true);

        // Chủ sở hữu thấy
        Optional<Transaction> found = transactionRepository.findByIdAndUserIdAndIsDeletedFalse(active.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(active.getId());

        // User khác không thấy
        assertThat(transactionRepository.findByIdAndUserIdAndIsDeletedFalse(active.getId(), otherUserId)).isEmpty();

        // Xóa mềm không thấy
        assertThat(transactionRepository.findByIdAndUserIdAndIsDeletedFalse(deleted.getId(), userId)).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId: Tìm thấy giao dịch kể cả đã xóa mềm")
    void findByIdAndUserId_ReturnsDeleted() {
        Transaction deleted = createTransaction(userId, TxnType.EXPENSE, 20_000, Instant.now(), "Snack", true);

        Optional<Transaction> found = transactionRepository.findByIdAndUserId(deleted.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(deleted.getId());
        assertThat(transactionRepository.findByIdAndUserId(deleted.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndIsDeletedFalse: Tìm thấy giao dịch chưa xóa")
    void findByIdAndIsDeletedFalse_ReturnsActive() {
        Transaction active = createTransaction(userId, TxnType.INCOME, 100_000, Instant.now(), "Salary", false);
        Transaction deleted = createTransaction(userId, TxnType.INCOME, 50_000, Instant.now(), "Bonus", true);

        assertThat(transactionRepository.findByIdAndIsDeletedFalse(active.getId())).isPresent();
        assertThat(transactionRepository.findByIdAndIsDeletedFalse(deleted.getId())).isEmpty();
    }
}
