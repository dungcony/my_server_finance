package com.datn.financeapp.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.wallet.dto.request.TransferRequest;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
import com.datn.financeapp.wallet.service.WalletTransferService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * D-28 mục 1: hai luồng cùng gọi transfer song song trên cùng ví, chứng minh
 * {@link WalletRepository#findByIdForUpdate} + {@link WalletRepository#adjustBalance} không để
 * mất cập nhật (lost-update). Gọi thẳng {@link WalletTransferService} trong cùng JVM qua Spring
 * context — không qua MockMvc/HTTP vì mục tiêu là chứng minh tầng DB lock hoạt động đúng.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class WalletTransferConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci13YWxsZXQtY29uY3VycmVuY3k=");
    }

    @Autowired
    private WalletTransferService walletTransferService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM transactions");
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @RepeatedTest(5)
    void twoConcurrentTransfers_sameWallets_noLostUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("concurrency-" + userId + "@example.com")
                .password("hash")
                .firstName("Người Kiểm Thử Đồng Thời")
                .plan(com.datn.financeapp.user.enums.UserPlan.FREE)
                .createdAt(Instant.now())
                .build();
        userRepository.save(user);

        Wallet walletA = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Ví A")
                .type("bank")
                .initialBalance(10_000_000L)
                .currentBalance(10_000_000L)
                .includeInTotal(true)
                .sortOrder(0)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build();
        Wallet walletB = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Ví B")
                .type("bank")
                .initialBalance(0L)
                .currentBalance(0L)
                .includeInTotal(true)
                .sortOrder(1)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build();
        walletRepository.save(walletA);
        walletRepository.save(walletB);

        TransferRequest req = new TransferRequest(walletA.getId(), walletB.getId(), 2_000_000L, null, null, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<?> future1 = executor.submit(() -> {
            awaitLatch(startLatch);
            walletTransferService.transfer(userId, req);
        });
        Future<?> future2 = executor.submit(() -> {
            awaitLatch(startLatch);
            walletTransferService.transfer(userId, req);
        });

        startLatch.countDown();
        future1.get();
        future2.get();
        executor.shutdown();

        Wallet finalA = walletRepository.findById(walletA.getId()).orElseThrow();
        Wallet finalB = walletRepository.findById(walletB.getId()).orElseThrow();

        assertThat(finalA.getCurrentBalance()).isEqualTo(6_000_000L);
        assertThat(finalB.getCurrentBalance()).isEqualTo(4_000_000L);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
