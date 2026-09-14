package com.datn.financeapp.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.entity.RefreshToken;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
class RefreshTokenRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hdXRoLXByb2ZpbGUtdGVzdC0zMmI=");
    }

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID userId;

    @BeforeEach
    void cleanTables() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("rt.repo.test@example.com")
                .password("hashed_pwd")
                .firstName("RT")
                .lastName("User")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build();
        user = userRepository.saveAndFlush(user);
        this.userId = user.getId();
    }

    private void createToken(String hash, boolean isRevoked) {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(hash)
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .revokedAt(isRevoked ? Instant.now() : null)
                .deviceInfo("JUnit Test Device")
                .createdAt(Instant.now())
                .build();
        refreshTokenRepository.saveAndFlush(token);
    }

    @Test
    @DisplayName("findActiveByTokenHashForUpdate: Chỉ trả về token khi revokedAt là null")
    void findActiveByTokenHashForUpdate_ReturnsActiveTokenOnly() {
        createToken("active-hash-111", false);
        createToken("revoked-hash-222", true);

        Optional<RefreshToken> active = refreshTokenRepository.findActiveByTokenHashForUpdate("active-hash-111");
        assertThat(active).isPresent();
        assertThat(active.get().getTokenHash()).isEqualTo("active-hash-111");

        Optional<RefreshToken> revoked = refreshTokenRepository.findActiveByTokenHashForUpdate("revoked-hash-222");
        assertThat(revoked).isEmpty();
    }

    @Test
    @DisplayName("findByTokenHash: Trả về token bất kể đã revoked hay chưa (dùng cho reuse-detection)")
    void findByTokenHash_ReturnsTokenRegardlessOfRevocation() {
        createToken("revoked-hash-333", true);

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("revoked-hash-333");
        assertThat(found).isPresent();
        assertThat(found.get().getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("revokeAllActiveForUser: Thu hồi toàn bộ token active của user")
    void revokeAllActiveForUser_RevokesAllAndReturnsCount() {
        createToken("token-a", false);
        createToken("token-b", false);
        createToken("token-c-already-revoked", true);

        int updatedCount = refreshTokenRepository.revokeAllActiveForUser(userId);
        assertThat(updatedCount).isEqualTo(2);

        List<RefreshToken> activeLeft = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        assertThat(activeLeft).isEmpty();
    }

    @Test
    @DisplayName("revokeByTokenHash: Thu hồi đúng 1 token theo hash")
    void revokeByTokenHash_RevokesSpecificToken() {
        createToken("token-specific", false);

        int updatedCount = refreshTokenRepository.revokeByTokenHash("token-specific");
        assertThat(updatedCount).isEqualTo(1);

        Optional<RefreshToken> token = refreshTokenRepository.findActiveByTokenHashForUpdate("token-specific");
        assertThat(token).isEmpty();
    }

    @Test
    @DisplayName("findAllByUserIdAndRevokedAtIsNull: Chỉ lấy danh sách token còn active của user")
    void findAllByUserIdAndRevokedAtIsNull_FiltersActiveOnly() {
        createToken("token-active-1", false);
        createToken("token-active-2", false);
        createToken("token-revoked-3", true);

        List<RefreshToken> list = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        assertThat(list).hasSize(2);
        assertThat(list).allMatch(t -> t.getRevokedAt() == null);
    }
}
