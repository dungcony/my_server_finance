package com.datn.financeapp.integrationTest.wallet.repository;

import com.datn.financeapp.module.wallet.repository.*;


import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.enums.WalletType;

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
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM transactions");
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
                .build());
        otherUserId = otherUser.getId();
    }

    private Wallet createWallet(UUID ownerId, String name, WalletType type, long balance, boolean deleted) {
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .name(name)
                .type(type)
                .balance(balance)
                .iconCode("default_wallet")
                .color("#112233")
                .isDefault(false)
                .build();
        wallet.setIsDeleted(deleted);
        return walletRepository.saveAndFlush(wallet);
    }

    @Test
    @DisplayName("countByUserIdAndIsDeletedFalse & existsByUserIdAndNameAndIsDeletedFalse")
    void countAndExists_FilterActiveWallets() {
        createWallet(userId, "Cash", WalletType.CASH, 100_000, false);
        createWallet(userId, "Deleted Bank", WalletType.BANK, 500_000, true);

        assertThat(walletRepository.countByUserIdAndIsDeletedFalse(userId)).isEqualTo(1);
        assertThat(walletRepository.existsByUserIdAndNameAndIsDeletedFalse(userId, "Cash")).isTrue();
        assertThat(walletRepository.existsByUserIdAndNameAndIsDeletedFalse(userId, "Deleted Bank")).isFalse();
    }

    @Test
    @DisplayName("insertIfNameNotExist: Chèn thành công nếu tên chưa tồn tại, trả về 0 nếu trùng tên")
    void insertIfNameNotExist_HandlesDuplicates() {
        Wallet w1 = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Vi Tiet Kiem")
                .type(WalletType.SAVINGS)
                .balance(0L)
                .iconCode("default_wallet")
                .color("#112233")
                .isDefault(false)
                .build();

        int inserted1 = walletRepository.insertIfNameNotExist(w1);
        assertThat(inserted1).isEqualTo(1);

        Wallet w2 = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Vi Tiet Kiem")
                .type(WalletType.BANK)
                .balance(100_000L)
                .iconCode("default_wallet")
                .color("#112233")
                .isDefault(false)
                .build();

        int inserted2 = walletRepository.insertIfNameNotExist(w2);
        assertThat(inserted2).isEqualTo(0);
    }

    @Test
    @DisplayName("findByIdAndUserIdAndIsDeleted: Lọc đúng quyền sở hữu và trạng thái xoá mềm")
    void findByIdAndUserIdAndIsDeleted_EnforcesSecurityAndState() {
        Wallet active = createWallet(userId, "My Wallet", WalletType.BANK, 500_000, false);
        Wallet deleted = createWallet(userId, "Old Wallet", WalletType.BANK, 100_000, true);

        Optional<Wallet> found = walletRepository.findByIdAndUserIdAndIsDeleted(active.getId(), userId, false);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(active.getId());

        // Khác user -> empty
        assertThat(walletRepository.findByIdAndUserIdAndIsDeleted(active.getId(), otherUserId, false)).isEmpty();

        // Đã xoá mềm -> empty khi tìm false
        assertThat(walletRepository.findByIdAndUserIdAndIsDeleted(deleted.getId(), userId, false)).isEmpty();
        // Đã xoá mềm -> tìm thấy khi tìm true
        assertThat(walletRepository.findByIdAndUserIdAndIsDeleted(deleted.getId(), userId, true)).isPresent();
    }

    @Test
    @DisplayName("findByIdAndUserId: Tìm thấy ví kể cả đã xoá mềm của chính user")
    void findByIdAndUserId_ReturnsWalletRegardlessOfDeleted() {
        Wallet deleted = createWallet(userId, "Deleted Wallet", WalletType.CASH, 0, true);

        Optional<Wallet> found = walletRepository.findByIdAndUserId(deleted.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(deleted.getId());
        assertThat(walletRepository.findByIdAndUserId(deleted.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndIsDeletedFalse: Lấy ví chưa xoá theo id")
    void findByIdAndIsDeletedFalse_ReturnsActiveWallet() {
        Wallet active = createWallet(userId, "Active Wallet", WalletType.CASH, 200_000, false);
        Wallet deleted = createWallet(userId, "Deleted Wallet", WalletType.CASH, 0, true);

        assertThat(walletRepository.findByIdAndIsDeletedFalse(active.getId())).isPresent();
        assertThat(walletRepository.findByIdAndIsDeletedFalse(deleted.getId())).isEmpty();
    }
}
