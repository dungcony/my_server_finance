package com.datn.financeapp.group.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
class GroupTransactionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ndHhuLXJlcG8tdGVzdA==");
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private GroupTransactionRepository groupTransactionRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.datn.financeapp.category.repository.CategoryRepository categoryRepository;

    private User testUser;
    private Group testGroup;
    private GTransaction testTxn;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("test.gtxn." + UUID.randomUUID() + "@example.com")
                .password("hashed_password")
                .firstName("Chi")
                .lastName("Tieu")
                .status(UserStatus.ACTIVE)
                .plan(UserPlan.FREE)
                .createdAt(Instant.now())
                .build());

        testGroup = groupRepository.save(Group.builder()
                .id(UUID.randomUUID())
                .name("Nhóm Giao Dịch Test")
                .inviteCode("TXN12345")
                .status(GroupStatus.ACTIVE)
                .isSettlementEnabled(true)
                .isJoinWithoutConfirm(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        com.datn.financeapp.category.entity.Category testCategory = categoryRepository.findSystemCategoryByName("Ăn uống", "expense")
                .orElseGet(() -> categoryRepository.findAll().stream().findFirst().orElseThrow());

        testTxn = groupTransactionRepository.save(GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(testGroup.getId())
                .moneySource(MoneySource.FUND)
                .transactorId(testUser.getId())
                .createdBy(testUser.getId())
                .categoryId(testCategory.getId())
                .type(TransactionType.EXPENSE)
                .status(TransactionStatus.CONFIRMED)
                .reviewedBy(testUser.getId())
                .reviewedAt(Instant.now())
                .amount(100_000L)
                .occurredAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .note("Mua sắm nhóm")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .participants(List.of(
                        TransactionParticipant.builder()
                                .userId(testUser.getId())
                                .shareAmount(100_000L)
                                .build()
                ))
                .build());
    }

    @Test
    @DisplayName("Ghi giao dịch khi bản ghi đã bị request khác sửa trước thì bị từ chối thay vì ghi đè")
    void saveAndFlush_WhenRowChangedByAnotherTransaction_ThrowsOptimisticLockFailure() {
        entityManager.flush();
        // request kia đã ghi trước: DB đã đổi bản ghi trong khi entity này vẫn cầm trạng thái cũ
        entityManager.createNativeQuery("UPDATE group_transactions SET version = version + 1 WHERE id = :id")
                .setParameter("id", testTxn.getId())
                .executeUpdate();

        testTxn.setStatus(TransactionStatus.REJECTED);

        assertThatThrownBy(() -> groupTransactionRepository.saveAndFlush(testTxn))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Tìm giao dịch theo ID, Group ID và chưa bị xóa mềm")
    void findByIdAndGroupIdAndDeletedAtIsNull_success() {
        Optional<GTransaction> txnOpt = groupTransactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(
                testTxn.getId(), testGroup.getId()
        );
        assertThat(txnOpt).isPresent();
        assertThat(txnOpt.get().getAmount()).isEqualTo(100_000L);
        assertThat(txnOpt.get().getParticipants()).hasSize(1);
    }

    @Test
    @DisplayName("Không tìm thấy giao dịch đã bị xóa mềm")
    void findByIdAndGroupIdAndDeletedAtIsNull_deleted() {
        testTxn.setDeletedAt(Instant.now());
        groupTransactionRepository.save(testTxn);

        Optional<GTransaction> txnOpt = groupTransactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(
                testTxn.getId(), testGroup.getId()
        );
        assertThat(txnOpt).isEmpty();
    }

    @Test
    @DisplayName("Tìm danh sách theo ID, Group ID, chưa xoá và đúng status")
    void findByIdInAndGroupIdAndDeletedAtIsNullAndStatus_success() {
        List<GTransaction> list = groupTransactionRepository.findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(
                List.of(testTxn.getId()), testGroup.getId(), TransactionStatus.CONFIRMED
        );
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(testTxn.getId());
    }

    @Test
    @DisplayName("Đếm số lượng giao dịch theo trạng thái chưa bị xóa")
    void countByGroupIdAndStatusAndDeletedAtIsNull_success() {
        long count = groupTransactionRepository.countByGroupIdAndStatusAndDeletedAtIsNull(
                testGroup.getId(), TransactionStatus.CONFIRMED
        );
        assertThat(count).isEqualTo(1L);

        long pendingCount = groupTransactionRepository.countByGroupIdAndStatusAndDeletedAtIsNull(
                testGroup.getId(), TransactionStatus.PENDING
        );
        assertThat(pendingCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("Tính tổng số tiền giao dịch đã CONFIRMED trong khoảng thời gian")
    void sumAmountByGroupIdAndTypeAndPeriod_success() {
        Instant from = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant to = Instant.now();

        Long sum = groupTransactionRepository.sumAmountByGroupIdAndTypeAndPeriod(
                testGroup.getId(), TransactionType.EXPENSE, from, to
        );
        assertThat(sum).isEqualTo(100_000L);

        Long zeroSum = groupTransactionRepository.sumAmountByGroupIdAndTypeAndPeriod(
                testGroup.getId(), TransactionType.CONTRIBUTION, from, to
        );
        assertThat(zeroSum).isEqualTo(0L);
    }
}
