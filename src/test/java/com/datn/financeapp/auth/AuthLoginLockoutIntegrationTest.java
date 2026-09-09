package com.datn.financeapp.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.entity.LoginAttempt;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test tích hợp qua HTTP thật (MockMvc + AuthController + AuthService + Testcontainers
 * PostgreSQL) cho AUTH-07 (khoá đăng nhập 15 phút sau 5 lần sai liên tiếp) — ưu tiên test thứ 3
 * theo D-23. Khác Plan 04 (test Service layer), file này verify toàn bộ chuỗi Filter ->
 * Controller -> Service -> DB hoạt động đúng qua HTTP thật.
 *
 * RateLimitFilter thật bị thay no-op — cùng lý do/pattern như AuthRegisterLoginIntegrationTest
 * (nhiều request /auth/login liên tiếp trong 1 test case sẽ bị 429 giả nếu giữ filter thật).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthLoginLockoutIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hdXRoLWxvY2tvdXQtdGVzdC0zMmI=");
    }

    @TestConfiguration
    static class NoRateLimitConfig {
        @Bean
        @Primary
        RateLimitFilter rateLimitFilter() {
            return new RateLimitFilter(null) {
                @Override
                protected void doFilterInternal(
                        HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws ServletException, IOException {
                    chain.doFilter(req, res);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @BeforeEach
    void cleanTables() {
        loginAttemptRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void register(String email, String password, String username) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", password, "username", username);
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private void loginWithWrongPassword(String email, String wrongPassword) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", wrongPassword);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void after5FailedAttempts_6thWithCorrectPassword_isStillLocked() throws Exception {
        String email = "khoa.5lan@example.com";
        String correctPassword = "matkhaudung1";
        register(email, correctPassword, "Khoá 5 Lần");

        for (int i = 0; i < 5; i++) {
            loginWithWrongPassword(email, "sai-mat-khau-" + i);
        }

        Map<String, Object> correctLogin = Map.of("email", email, "password", correctPassword);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctLogin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void lockoutExpiresNaturally_LoginWithCorrectPasswordSucceedsAgain() throws Exception {
        String email = "khoa.het.han@example.com";
        String correctPassword = "matkhaudung1";
        register(email, correctPassword, "Khoá Hết Hạn");

        for (int i = 0; i < 5; i++) {
            loginWithWrongPassword(email, "sai-mat-khau-" + i);
        }

        // Xác nhận đang bị khoá.
        Map<String, Object> correctLogin = Map.of("email", email, "password", correctPassword);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctLogin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));

        // Thao túng trực tiếp attempted_at của TOÀN BỘ 5 bản ghi sai lùi hơn 15 phút trước —
        // isLockedOut() tính động mỗi lần gọi (không cron job riêng), khoá tự hết hạn theo thời
        // gian.
        List<LoginAttempt> attempts = loginAttemptRepository.findTop5ByEmailOrderByAttemptedAtDesc(email);
        Instant sixteenMinutesAgo = Instant.now().minus(16, ChronoUnit.MINUTES);
        for (LoginAttempt attempt : attempts) {
            attempt.setAttemptedAt(sixteenMinutesAgo);
            loginAttemptRepository.save(attempt);
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty());
    }

    @Test
    void oneCorrectAttemptInterspersedAmong5Failures_isNotLocked() throws Exception {
        String email = "chen.giua@example.com";
        String correctPassword = "matkhaudung1";
        register(email, correctPassword, "Chen Giữa");

        // sai, sai, ĐÚNG, sai, sai, sai -> 5 bản ghi gần nhất không phải toàn bộ succeeded=false.
        loginWithWrongPassword(email, "sai-1");
        loginWithWrongPassword(email, "sai-2");

        Map<String, Object> correctLogin = Map.of("email", email, "password", correctPassword);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctLogin)))
                .andExpect(status().isOk());

        loginWithWrongPassword(email, "sai-3");
        loginWithWrongPassword(email, "sai-4");
        loginWithWrongPassword(email, "sai-5");

        // 5 bản ghi gần nhất: đúng, sai, sai, sai (chỉ 4 lần sai liên tiếp gần nhất + 1 đúng
        // chen giữa) -> KHÔNG đủ điều kiện khoá (allFailed = false).
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctLogin)))
                .andExpect(status().isOk());
    }
}
