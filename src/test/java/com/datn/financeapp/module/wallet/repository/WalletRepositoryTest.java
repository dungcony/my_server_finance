package com.datn.financeapp.module.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.module.wallet.entity.Wallet;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.datn.financeapp.module.wallet.repository.WalletRepository;
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

    private long createdOffsetMillis;

    private Wallet createWallet(UUID ownerId, String name, String type, long balance, boolean inTotal, boolean deleted) {
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .name(name)
                .type(type)
                .initialBalance(balance)
                .currentBalance(balance)
                .includeInTotal(inTotal)
                .color("#112233")
                .isDeleted(deleted)
                // lệch nhau 1ms để thứ tự tạo (ORDER BY created_at) ổn định trong test
                .createdAt(Instant.now().plusMillis(createdOffsetMillis++))
                .build();
        return walletRepository.saveAndFlush(wallet);
    }

    @Test
    @DisplayName("countByUserIdAndIsDeletedFalse & existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse")
    void countAndExists_FilterActiveWallets() {
        createWallet(userId, "Cash", "cash", 100_000, true, false);
        createWallet(userId, "Deleted Bank", "bank", 500_000, true, true);

        // ví "Tiền mặt" mặc định do trigger tạo cùng người dùng, cộng thêm ví "Cash" vừa tạo
        assertThat(walletRepository.countByUserIdAndIsDeletedFalse(userId)).isEqualTo(2);
        assertThat(walletRepository.existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse(userId, "cash")).isTrue();
        assertThat(walletRepository.existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse(userId, "CASH")).isTrue();
        assertThat(walletRepository.existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse(userId, "Deleted Bank")).isFalse();
    }

    @Test
    @DisplayName("findByIdForUser: Chỉ trả về ví của chính user và chưa bị xóa mềm")
    void findByIdForUser_EnforcesIsolationAndSoftDelete() {
        Wallet activeWallet = createWallet(userId, "My Wallet", "bank", 500_000, true, false);
        Wallet deletedWallet = createWallet(userId, "Old Wallet", "bank", 100_000, true, true);

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
        Wallet deletedWallet = createWallet(userId, "Deleted Wallet", "cash", 0, true, true);

        Optional<Wallet> found = walletRepository.findByIdForUserIncludingDeleted(deletedWallet.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(deletedWallet.getId());
        assertThat(walletRepository.findByIdForUserIncludingDeleted(deletedWallet.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("findAllForUser: Lọc theo type, onlyInTotal và sắp xếp theo thứ tự tạo")
    void findAllForUser_FiltersAndSortsCorrectly() {
        Wallet w1 = createWallet(userId, "Bank 1", "bank", 1_000_000, true, false);
        Wallet w2 = createWallet(userId, "Cash 1", "cash", 200_000, true, false);
        Wallet w3 = createWallet(userId, "Bank Excluded", "bank", 500_000, false, false);
        createWallet(userId, "Deleted", "bank", 100_000, true, true);

        UUID defaultWalletId = walletRepository.findDefaultWalletId(userId).orElseThrow();

        // Lấy tất cả ví active của user (bỏ ví mặc định do trigger tạo để so thứ tự các ví của test)
        List<Wallet> all = walletRepository.findAllForUser(userId, null, null, false);
        assertThat(all).extracting(Wallet::getId).filteredOn(id -> !id.equals(defaultWalletId))
                .containsExactly(w1.getId(), w2.getId(), w3.getId());

        // Lọc theo type = bank
        List<Wallet> banks = walletRepository.findAllForUser(userId, "bank", null, false);
        assertThat(banks).extracting(Wallet::getId).containsExactly(w1.getId(), w3.getId());

        // Lọc theo onlyInTotal = true
        List<Wallet> inTotal = walletRepository.findAllForUser(userId, null, true, false);
        assertThat(inTotal).extracting(Wallet::getId).filteredOn(id -> !id.equals(defaultWalletId))
                .containsExactly(w1.getId(), w2.getId());
    }

    @Test
    @DisplayName("findDefaultWalletId: Trigger tạo user sinh sẵn ví mặc định, mỗi user một ví riêng")
    void findDefaultWalletId_ReturnsWalletCreatedByTrigger() {
        UUID defaultId = walletRepository.findDefaultWalletId(userId).orElseThrow();

        Wallet defaultWallet = walletRepository.findById(defaultId).orElseThrow();
        assertThat(defaultWallet.getName()).isEqualTo("Tiền mặt");
        assertThat(defaultWallet.getType()).isEqualTo("cash");
        assertThat(defaultWallet.getIsDefault()).isTrue();
        assertThat(walletRepository.findDefaultWalletId(otherUserId)).isPresent().isNotEqualTo(Optional.of(defaultId));
    }

    @Test
    @DisplayName("clearDefault rồi đặt ví khác làm mặc định: kiểm tra hoãn đến commit, còn đúng một ví mặc định")
    void clearDefault_ThenSetAnother_KeepsExactlyOneDefault() {
        Wallet other = createWallet(userId, "Bank", "bank", 0, true, false);

        walletRepository.clearDefault(userId);
        entityManager.clear();
        Wallet reloaded = walletRepository.findById(other.getId()).orElseThrow();
        reloaded.setIsDefault(true);
        walletRepository.saveAndFlush(reloaded);

        // ép chạy kiểm tra đang hoãn, hợp lệ thì không ném lỗi
        enforceOneDefaultRule();
        assertThat(walletRepository.findDefaultWalletId(userId)).contains(other.getId());
    }

    @Test
    @DisplayName("Xoá mềm ví mặc định: trigger chặn vì user còn 0 ví mặc định")
    void softDeleteDefaultWallet_ViolatesOneDefaultRule() {
        UUID defaultId = walletRepository.findDefaultWalletId(userId).orElseThrow();

        entityManager.createNativeQuery("UPDATE wallets SET is_deleted = TRUE WHERE id = :id")
                .setParameter("id", defaultId)
                .executeUpdate();

        assertThatThrownBy(this::enforceOneDefaultRule).hasStackTraceContaining("đúng một ví mặc định");
    }

    @Test
    @DisplayName("Hai ví mặc định cùng lúc: trigger chặn vì user có 2 ví mặc định")
    void twoDefaultWallets_ViolatesOneDefaultRule() {
        Wallet other = createWallet(userId, "Bank", "bank", 0, true, false);

        entityManager.createNativeQuery("UPDATE wallets SET is_default = TRUE WHERE id = :id")
                .setParameter("id", other.getId())
                .executeUpdate();

        assertThatThrownBy(this::enforceOneDefaultRule).hasStackTraceContaining("đúng một ví mặc định");
    }

    // trigger kiểm tra khai báo DEFERRABLE INITIALLY DEFERRED nên test phải ép chạy sớm vì test không commit
    private void enforceOneDefaultRule() {
        entityManager.flush();
        entityManager.createNativeQuery("SET CONSTRAINTS trg_wallets_one_default IMMEDIATE").executeUpdate();
    }

    @Test
    @DisplayName("findByIdForUpdate: Tìm kiếm với khoá bi quan PESSIMISTIC_WRITE")
    void findByIdForUpdate_FindsActiveWallet() {
        Wallet wallet = createWallet(userId, "Lock Test", "bank", 2_000_000, true, false);

        Optional<Wallet> locked = walletRepository.findByIdForUpdate(wallet.getId());
        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(wallet.getId());
    }

    @Test
    @DisplayName("adjustBalance & findCurrentBalanceNative: Cập nhật atomic số dư và đọc native SQL")
    void adjustBalance_And_FindCurrentBalanceNative() {
        Wallet wallet = createWallet(userId, "Balance Test", "bank", 1_000_000, true, false);

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
        Wallet active = createWallet(userId, "Active", "cash", 50_000, true, false);
        createWallet(userId, "Deleted", "cash", 50_000, true, true);

        List<UUID> ids = walletRepository.findAllActiveWalletIds();
        assertThat(ids).contains(active.getId());
    }
}
