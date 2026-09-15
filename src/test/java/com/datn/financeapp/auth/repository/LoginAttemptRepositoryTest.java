package com.datn.financeapp.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.entity.LoginAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
class LoginAttemptRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hdXRoLXByb2ZpbGUtdGVzdC0zMmI=");
    }

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @BeforeEach
    void cleanTables() {
        loginAttemptRepository.deleteAll();
    }

    @Test
    @DisplayName("findTop5ByEmailOrderByAttemptedAtDesc: Lấy đúng 5 lần đăng nhập mới nhất của email theo thứ tự giảm dần")
    void findTop5ByEmailOrderByAttemptedAtDesc_ReturnsTopFiveDesc() {
        String email = "target.user@example.com";
        String otherEmail = "other.user@example.com";
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // Tạo 7 lần đăng nhập cho target email (cách nhau 1 phút)
        for (int i = 1; i <= 7; i++) {
            loginAttemptRepository.save(LoginAttempt.builder()
                    .id(UUID.randomUUID())
                    .email(email)
                    .ipAddress("127.0.0.1")
                    .succeeded(i % 2 == 0)
                    .userAgent("JUnit")
                    .attemptedAt(baseTime.plus(i, ChronoUnit.MINUTES))
                    .build());
        }

        // Tạo 1 lần đăng nhập cho email khác
        loginAttemptRepository.save(LoginAttempt.builder()
                .id(UUID.randomUUID())
                .email(otherEmail)
                .ipAddress("127.0.0.1")
                .succeeded(true)
                .userAgent("JUnit")
                .attemptedAt(baseTime.plus(10, ChronoUnit.MINUTES))
                .build());

        loginAttemptRepository.flush();

        List<LoginAttempt> top5 = loginAttemptRepository.findTop5ByEmailOrderByAttemptedAtDesc(email);

        assertThat(top5).hasSize(5);
        assertThat(top5).allMatch(attempt -> attempt.getEmail().equals(email));

        // Kiểm tra thứ tự giảm dần: phần tử đầu tiên là lần 7 (mới nhất), phần tử cuối là lần 3
        assertThat(top5.get(0).getAttemptedAt()).isEqualTo(baseTime.plus(7, ChronoUnit.MINUTES));
        assertThat(top5.get(1).getAttemptedAt()).isEqualTo(baseTime.plus(6, ChronoUnit.MINUTES));
        assertThat(top5.get(2).getAttemptedAt()).isEqualTo(baseTime.plus(5, ChronoUnit.MINUTES));
        assertThat(top5.get(3).getAttemptedAt()).isEqualTo(baseTime.plus(4, ChronoUnit.MINUTES));
        assertThat(top5.get(4).getAttemptedAt()).isEqualTo(baseTime.plus(3, ChronoUnit.MINUTES));
    }
}
