package com.datn.financeapp.wallet.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test tích hợp qua HTTP thật cho POST /wallets/transfer (WALLET-06, api/02-VI.md mục 8) —
 * Task 1 plan 02-04, Test 1-4 (happy path + validate). Test 5 (concurrency) nằm ở
 * {@link WalletTransferConcurrencyTest} riêng vì cần gọi thẳng Service qua Spring context.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
class WalletTransferIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci13YWxsZXQtdHJhbnNmZXItdGVzdA==");
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestAuthSupport authSupport;

    @Autowired
    private com.datn.financeapp.auth.repository.OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        // OTP nằm ở Redis, không bị Testcontainers PostgreSQL dọn hộ.
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM group_members");
        jdbcTemplate.update("DELETE FROM groups");
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private String createWallet(String token, String name, String type, long initialBalance) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", type,
                "initial_balance", initialBalance);
        String response = mockMvc.perform(post("/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("id");
    }

    @Test
    void validTransfer_sufficientBalance_returns201AndMovesAmountBetweenWallets() throws Exception {
        String token = registerAndGetAccessToken("chuyen.hople@example.com");
        String sourceId = createWallet(token, "Ví Nguồn", "bank", 10_000_000);
        String destId = createWallet(token, "Ví Đích", "bank", 0);

        Map<String, Object> body = Map.of(
                "source_wallet_id", sourceId,
                "destination_wallet_id", destId,
                "amount", 2_000_000);

        mockMvc.perform(post("/wallets/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transaction.type").value("transfer"))
                .andExpect(jsonPath("$.data.transaction.amount").value(2_000_000))
                .andExpect(jsonPath("$.data.new_balance.source_wallet").value(8_000_000))
                .andExpect(jsonPath("$.data.new_balance.destination_wallet").value(2_000_000));

        Long txnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE type = 'transfer' AND wallet_id = ? "
                        + "AND destination_wallet_id = ?",
                Long.class, java.util.UUID.fromString(sourceId), java.util.UUID.fromString(destId));
        assertThat(txnCount).isEqualTo(1);

        Long sourceBalance = jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?", Long.class, java.util.UUID.fromString(sourceId));
        Long destBalance = jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?", Long.class, java.util.UUID.fromString(destId));
        assertThat(sourceBalance).isEqualTo(8_000_000);
        assertThat(destBalance).isEqualTo(2_000_000);
    }

    @Test
    void sameSourceAndDestination_returns400() throws Exception {
        String token = registerAndGetAccessToken("chuyen.trung.vi@example.com");
        String walletId = createWallet(token, "Ví Duy Nhất", "bank", 1_000_000);

        Map<String, Object> body = Map.of(
                "source_wallet_id", walletId,
                "destination_wallet_id", walletId,
                "amount", 1_000);

        mockMvc.perform(post("/wallets/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SAME_SOURCE_AND_DESTINATION"));
    }

    @Test
    void insufficientBalance_withoutFailFlag_stillTransfersAndGoesNegative() throws Exception {
        String token = registerAndGetAccessToken("thieu.so.du.mac.dinh@example.com");
        String sourceId = createWallet(token, "Ví Thiếu 1", "bank", 100_000);
        String destId = createWallet(token, "Ví Nhận 1", "bank", 0);

        Map<String, Object> body = Map.of(
                "source_wallet_id", sourceId,
                "destination_wallet_id", destId,
                "amount", 500_000);

        mockMvc.perform(post("/wallets/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.new_balance.source_wallet").value(-400_000));
    }

    @Test
    void transferFromGroupWalletWithNullUserId_returns404NotFound() throws Exception {
        String token = registerAndGetAccessToken("chuyen.tu.vi.chung@example.com");
        String destId = createWallet(token, "Ví Đích", "bank", 0);

        java.util.UUID userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", java.util.UUID.class, "chuyen.tu.vi.chung@example.com");
        java.util.UUID groupId = java.util.UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO groups (id, name, created_by_id, invite_code, invite_code_expires_at) "
                        + "VALUES (?, ?, ?, ?, now() + interval '7 days')",
                groupId, "Nhóm Kiểm Thử Ví Chung", userId, "GRP" + System.nanoTime());
        java.util.UUID groupWalletId = java.util.UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO wallets (id, user_id, group_id, name, type, initial_balance, current_balance) "
                        + "VALUES (?, NULL, ?, ?, 'bank', 1000000, 1000000)",
                groupWalletId, groupId, "Ví Chung Nhóm");

        Map<String, Object> body = Map.of(
                "source_wallet_id", groupWalletId.toString(),
                "destination_wallet_id", destId,
                "amount", 100_000);

        mockMvc.perform(post("/wallets/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void insufficientBalance_withFailFlagTrue_returns422() throws Exception {
        String token = registerAndGetAccessToken("thieu.so.du.chan@example.com");
        String sourceId = createWallet(token, "Ví Thiếu 2", "bank", 100_000);
        String destId = createWallet(token, "Ví Nhận 2", "bank", 0);

        Map<String, Object> body = Map.of(
                "source_wallet_id", sourceId,
                "destination_wallet_id", destId,
                "amount", 500_000,
                "fail_if_insufficient", true);

        mockMvc.perform(post("/wallets/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_BALANCE"));
    }
}
