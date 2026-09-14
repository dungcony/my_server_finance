package com.datn.financeapp.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.entity.RefreshToken;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Test tích hợp AUTH-03: rotation dùng một lần + reuse detection (thu hồi toàn bộ phiên khi
 * token đã revoke bị gửi lại) + race condition 2 request refresh đồng thời cùng token
 * (Pitfall 7 — chỉ đúng 1 request được thành công nhờ SELECT ... FOR UPDATE).
 *
 * RateLimitFilter thật thay bằng no-op (xem AuthRegisterLoginIntegrationTest) — bucket 5/phút/IP
 * dùng chung sẽ chặn nhầm các test gọi /auth/register|login|refresh nhiều lần trong 1 JVM run.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
class AuthRefreshRotationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1yZWZyZXNoLXJvdGF0aW9uLXRlc3Q=");
    }

    @TestConfiguration
    static class NoRateLimitConfig {
        // Tên method PHẢI khớp "rateLimitFilter" — xem giải thích ở AuthRegisterLoginIntegrationTest.
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
    private OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Thẻ làm mới đến từ {@code /auth/verify-email} chứ không phải {@code /auth/register}: từ
     * 13/09/2026 register chỉ tạo tài khoản {@code PENDING_VERIFY} và không cấp thẻ nào.
     */
    private String registerAndGetRefreshToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhaudung1",
                "username", "Người Test Refresh");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        String code = otpRepository
                .findByTypeAndEmail(OtpType.REGISTER_OTP, email)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy mã OTP đăng ký cho " + email))
                .getCode();

        String response = mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "code", code))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("refresh_token");
    }

    private UUID userIdOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void validRefresh_revokesOldToken_issuesNewToken() throws Exception {
        String rawOldToken = registerAndGetRefreshToken("refresh.hop.le@example.com");
        UUID userId = userIdOf("refresh.hop.le@example.com");

        Map<String, Object> body = Map.of("refresh_token", rawOldToken);
        String response = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.refresh_token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        String newRawToken = (String) data.get("refresh_token");
        assertThat(newRawToken).isNotEqualTo(rawOldToken);

        List<RefreshToken> active = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        assertThat(active).hasSize(1);
    }

    @Test
    void reuseOfRevokedToken_revokesAllSessions_returns401RefreshTokenInvalid() throws Exception {
        String rawOldToken = registerAndGetRefreshToken("reuse.detect@example.com");
        UUID userId = userIdOf("reuse.detect@example.com");

        Map<String, Object> body = Map.of("refresh_token", rawOldToken);
        // Lần 1: refresh hợp lệ, token cũ bị revoke, cấp token mới.
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // Lần 2: gửi lại đúng token CŨ đã revoke — dấu hiệu reuse, phải thu hồi TOÀN BỘ phiên.
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));

        List<RefreshToken> active = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        assertThat(active).isEmpty();
    }

    @Test
    void twoConcurrentRefreshRequestsWithSameToken_onlyOneSucceeds() throws Exception {
        String rawToken = registerAndGetRefreshToken("race.condition@example.com");

        Map<String, Object> body = Map.of("refresh_token", rawToken);
        String jsonBody = objectMapper.writeValueAsString(body);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        try {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> callRefresh(jsonBody, readyLatch, startLatch)),
                    executor.submit(() -> callRefresh(jsonBody, readyLatch, startLatch)));

            readyLatch.await(10, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<Integer> future : futures) {
                int status = future.get(15, TimeUnit.SECONDS);
                if (status == 200) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                }
            }
        } finally {
            executor.shutdown();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
    }

    private int callRefresh(String jsonBody, CountDownLatch readyLatch, CountDownLatch startLatch) throws Exception {
        readyLatch.countDown();
        startLatch.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void logoutWithoutLogoutAllDevices_revokesOnlyCurrentToken_otherTokenStaysActive() throws Exception {
        String email = "logout.mot.thiet.bi@example.com";
        String rawFirstToken = registerAndGetRefreshToken(email);
        UUID userId = userIdOf(email);

        // Đăng nhập lần 2 để có thêm 1 refresh token active khác (giả lập thiết bị thứ hai).
        Map<String, Object> loginBody = Map.of("email", email, "password", "matkhaudung1");
        String loginResponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(loginResponse, Map.class);
        String rawSecondToken = (String) ((Map<?, ?>) parsed.get("data")).get("refresh_token");

        Map<String, Object> logoutBody = Map.of("refresh_token", rawFirstToken, "logout_all_devices", false);
        mockMvc.perform(post("/auth/logout")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                        userId.toString(), null, java.util.Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutBody)))
                .andExpect(status().isOk());

        List<RefreshToken> active = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        assertThat(active).hasSize(1);
        // Token còn active phải là token thứ hai (đăng nhập lần 2), không phải token vừa logout.
        String activeRawTokenHash = sha256Hex(rawSecondToken);
        assertThat(active.get(0).getTokenHash()).isEqualTo(activeRawTokenHash);
    }

    private static String sha256Hex(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
