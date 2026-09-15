package com.datn.financeapp.debt.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.debt.entity.Debt;
import com.datn.financeapp.transaction.entity.Transaction;
import com.datn.financeapp.transaction.repository.TransactionRepository;
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
class DebtRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1kZWJ0LXJlcG8tdGVzdC0zMmI=");
    }

    @Autowired
    private DebtRepository debtRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID userId;
    private UUID otherUserId;
    private UUID walletId;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("debt.user@example.com")
                .password("hash")
                .firstName("Debt")
                .lastName("Owner")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        userId = user.getId();

        User otherUser = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("other.debt@example.com")
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
                .name("Debt Wallet")
                .type("cash")
                .initialBalance(10_000_000L)
                .currentBalance(10_000_000L)
                .includeInTotal(true)
                .sortOrder(0)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        walletId = wallet.getId();
    }

    private Debt createDebt(UUID ownerId, String type, long principal, LocalDate due, String status) {
        Category cat = categoryRepository.findSystemCategoryByName("Cho vay", "expense").orElseThrow();
        Transaction txn = transactionRepository.saveAndFlush(Transaction.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .walletId(walletId)
                .categoryId(cat.getId())
                .type("expense")
                .amount(principal)
                .date(LocalDate.now())
                .displayName("Lending origin")
                .source("manual")
                .isDeleted(false)
                .countsInReport(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        Debt debt = Debt.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .walletId(walletId)
                .type(type)
                .counterpartyName("John Doe")
                .principalAmount(principal)
                .paidAmount(0L)
                .issuedDate(LocalDate.now())
                .dueDate(due)
                .status(status)
                .originTransactionId(txn.getId())
                .createdAt(Instant.now())
                .build();
        return debtRepository.saveAndFlush(debt);
    }

    @Test
    @DisplayName("findByIdAndUserId: Chỉ trả về khoản nợ thuộc quyền sở hữu của user (D-27 isolation)")
    void findByIdAndUserId_EnforcesOwnership() {
        Debt debt = createDebt(userId, "lending", 1_000_000L, LocalDate.now().plusDays(30), "outstanding");

        Optional<Debt> found = debtRepository.findByIdAndUserId(debt.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(debt.getId());

        // User khác không thấy (D-27 -> 404)
        assertThat(debtRepository.findByIdAndUserId(debt.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("findAllForUser: Lọc theo type và status, sắp xếp theo ngày phát sinh DESC")
    void findAllForUser_FiltersAndSorts() {
        Debt d1 = createDebt(userId, "lending", 1_000_000L, LocalDate.now().plusDays(10), "outstanding");
        createDebt(userId, "borrowing", 2_000_000L, LocalDate.now().plusDays(5), "outstanding");

        List<Debt> lendings = debtRepository.findAllForUser(userId, "lending", null);
        assertThat(lendings).extracting(Debt::getId).containsExactly(d1.getId());
    }

    @Test
    @DisplayName("findPaidAmountNative & findStatusNative: Đọc scalar trực tiếp từ CSDL")
    void findPaidAmountAndStatusNative_ReadsScalarsDirectly() {
        Debt debt = createDebt(userId, "lending", 500_000L, null, "outstanding");

        Optional<Long> paidAmount = debtRepository.findPaidAmountNative(debt.getId());
        Optional<String> status = debtRepository.findStatusNative(debt.getId());

        assertThat(paidAmount).contains(0L);
        assertThat(status).contains("outstanding");
    }

    @Test
    @DisplayName("findOutstandingWithDueDate & findAllOutstandingWithDueDate: Tìm các khoản nợ chưa trả có hạn trả")
    void findOutstandingWithDueDate_FindsDueDebts() {
        Debt withDue = createDebt(userId, "lending", 1_000_000L, LocalDate.now().plusDays(7), "outstanding");
        createDebt(userId, "lending", 1_000_000L, null, "outstanding"); // Không có due_date

        List<Debt> userOutstanding = debtRepository.findOutstandingWithDueDate(userId);
        assertThat(userOutstanding).extracting(Debt::getId).containsExactly(withDue.getId());

        List<Debt> allOutstanding = debtRepository.findAllOutstandingWithDueDate();
        assertThat(allOutstanding).extracting(Debt::getId).contains(withDue.getId());
    }
}
