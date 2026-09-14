package com.datn.financeapp.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.wallet.entity.Wallet;
import java.time.Instant;
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
class WalletRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci13YWxsZXQtcmVwby10ZXN0LTMyYg==");
    }

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("wallet.user@example.com")
                .password("hash")
                .firstName("Wallet")
                .lastName("Owner")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        userId = user.getId();

        User otherUser = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("other.user@example.com")
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

    private Wallet createWallet(UUID ownerId, String name, String type, long balance, int sortOrder, boolean inTotal, boolean deleted) {
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .name(name)
                .type(type)
                .initialBalance(balance)
                .currentBalance(balance)
                .includeInTotal(inTotal)
                .color("#112233")
                .sortOrder(sortOrder)
                .isDeleted(deleted)
                .createdAt(Instant.now())
                .build();
        return walletRepository.saveAndFlush(wallet);
    }

    @Test
    @DisplayName("countByUserIdAndIsDeletedFalse & existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse")
    void countAndExists_FilterActiveWallets() {
        createWallet(userId, "Cash", "cash", 100_000, 0, true, false);
        createWallet(userId, "Deleted Bank", "bank", 500_000, 1, true, true);

        assertThat(walletRepository.countByUserIdAndIsDeletedFalse(userId)).isEqualTo(1);
        assertThat(walletRepository.existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse(userId, "cash")).isTrue();
        assertThat(walletRepository.existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse(userId, "CASH")).isTrue();
        assertThat(walletRepository.existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse(userId, "Deleted Bank")).isFalse();
    }

    @Test
    @DisplayName("findMaxSortOrderByUserId: Trả về max sortOrder hoặc -1 nếu chưa có ví")
    void findMaxSortOrderByUserId_ReturnsMaxOrNegativeOne() {
        assertThat(walletRepository.findMaxSortOrderByUserId(userId)).isEqualTo(-1);

        createWallet(userId, "Wallet 1", "cash", 100_000, 2, true, false);
        createWallet(userId, "Wallet 2", "bank", 200_000, 5, true, false);
        createWallet(userId, "Wallet Deleted", "bank", 300_000, 99, true, true);

        assertThat(walletRepository.findMaxSortOrderByUserId(userId)).isEqualTo(5);
    }

    @Test
    @DisplayName("findByIdForUser: Chỉ trả về ví của chính user và chưa bị xóa mềm")
    void findByIdForUser_EnforcesIsolationAndSoftDelete() {
        Wallet activeWallet = createWallet(userId, "My Wallet", "bank", 500_000, 0, true, false);
        Wallet deletedWallet = createWallet(userId, "Old Wallet", "bank", 100_000, 1, true, true);

        // Chủ sở hữu tìm thấy ví đang hoạt động
        Optional<Wallet> found = walletRepository.findByIdForUser(activeWallet.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(activeWallet.getId());

        // User khác không tìm thấy (trả về rỗng -> 404 thay vì 403 theo D-27)
        assertThat(walletRepository.findByIdForUser(activeWallet.getId(), otherUserId)).isEmpty();

        // Không trả về ví đã xóa mềm
        assertThat(walletRepository.findByIdForUser(deletedWallet.getId(), userId)).isEmpty();
    }

    @Test
    @DisplayName("findByIdForUserIncludingDeleted: Trả về cả ví đã xóa mềm của chính user (dùng cho idempotent delete)")
    void findByIdForUserIncludingDeleted_ReturnsDeletedWallet() {
        Wallet deletedWallet = createWallet(userId, "Deleted Wallet", "cash", 0, 0, true, true);

        Optional<Wallet> found = walletRepository.findByIdForUserIncludingDeleted(deletedWallet.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(deletedWallet.getId());
        assertThat(walletRepository.findByIdForUserIncludingDeleted(deletedWallet.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("findAllForUser: Lọc theo type, onlyInTotal và sắp xếp theo sortOrder")
    void findAllForUser_FiltersAndSortsCorrectly() {
        Wallet w1 = createWallet(userId, "Bank 1", "bank", 1_000_000, 1, true, false);
        Wallet w2 = createWallet(userId, "Cash 1", "cash", 200_000, 0, true, false);
        Wallet w3 = createWallet(userId, "Bank Excluded", "bank", 500_000, 2, false, false);
        createWallet(userId, "Deleted", "bank", 100_000, 3, true, true);

        // Lấy tất cả ví active của user
        List<Wallet> all = walletRepository.findAllForUser(userId, null, null, false);
        assertThat(all).extracting(Wallet::getId).containsExactly(w2.getId(), w1.getId(), w3.getId());

        // Lọc theo type = bank
        List<Wallet> banks = walletRepository.findAllForUser(userId, "bank", null, false);
        assertThat(banks).extracting(Wallet::getId).containsExactly(w1.getId(), w3.getId());

        // Lọc theo onlyInTotal = true
        List<Wallet> inTotal = walletRepository.findAllForUser(userId, null, true, false);
        assertThat(inTotal).extracting(Wallet::getId).containsExactly(w2.getId(), w1.getId());
    }

    @Test
    @DisplayName("updateSortOrder: Cập nhật sortOrder của ví")
    void updateSortOrder_UpdatesSuccessfully() {
        Wallet wallet = createWallet(userId, "Order Test", "cash", 100_000, 0, true, false);

        int updated = walletRepository.updateSortOrder(wallet.getId(), 10, userId);
        assertThat(updated).isEqualTo(1);

        entityManager.clear();
        Wallet reloaded = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(reloaded.getSortOrder()).isEqualTo(10);
    }

    @Test
    @DisplayName("findByIdForUpdate: Tìm kiếm với khoá bi quan PESSIMISTIC_WRITE")
    void findByIdForUpdate_FindsActiveWallet() {
        Wallet wallet = createWallet(userId, "Lock Test", "bank", 2_000_000, 0, true, false);

        Optional<Wallet> locked = walletRepository.findByIdForUpdate(wallet.getId());
        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(wallet.getId());
    }

    @Test
    @DisplayName("adjustBalance & findCurrentBalanceNative: Cập nhật atomic số dư và đọc native SQL")
    void adjustBalance_And_FindCurrentBalanceNative() {
        Wallet wallet = createWallet(userId, "Balance Test", "bank", 1_000_000, 0, true, false);

        // Cộng 250_000
        int updatedAdd = walletRepository.adjustBalance(wallet.getId(), 250_000);
        assertThat(updatedAdd).isEqualTo(1);
        assertThat(walletRepository.findCurrentBalanceNative(wallet.getId())).contains(1_250_000L);

        // Trừ 150_000
        int updatedSub = walletRepository.adjustBalance(wallet.getId(), -150_000);
        assertThat(updatedSub).isEqualTo(1);
        assertThat(walletRepository.findCurrentBalanceNative(wallet.getId())).contains(1_100_000L);
    }

    @Test
    @DisplayName("findAllActiveWalletIds: Trả về danh sách ID ví active cho job đối chiếu")
    void findAllActiveWalletIds_ReturnsOnlyActive() {
        Wallet active = createWallet(userId, "Active", "cash", 50_000, 0, true, false);
        createWallet(userId, "Deleted", "cash", 50_000, 1, true, true);

        List<UUID> ids = walletRepository.findAllActiveWalletIds();
        assertThat(ids).contains(active.getId());
    }
}
