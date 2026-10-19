package com.datn.financeapp.integrationTest.transaction.controller;

import com.datn.financeapp.module.transaction.controller.*;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class TransactionCrudIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci10eG4tY3J1ZC10ZXN0LTMyYg==");
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
    private OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private String createWallet(String token, String name, long initialBalance) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "CASH",
                "balance", initialBalance,
                "icon_code", "default_wallet",
                "color", "#2f8272");
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

    private String leafCategoryCode() {
        return jdbcTemplate.queryForObject(
                "SELECT code FROM categories WHERE is_deleted = false AND is_leaf = true LIMIT 1", String.class);
    }

    private String nonLeafCategoryCode() {
        return jdbcTemplate.queryForObject(
                "SELECT code FROM categories WHERE is_deleted = false AND is_leaf = false LIMIT 1", String.class);
    }

    @Test
    void createExpenseTransaction_deductsWalletBalance_returns201() throws Exception {
        String token = registerAndGetAccessToken("tao.chi@example.com");
        String walletId = createWallet(token, "Ví Chi Tiêu", 500_000);
        String categoryCode = leafCategoryCode();

        Map<String, Object> body = new HashMap<>();
        body.put("type", "EXPENSE");
        body.put("amount", 68000);
        body.put("wallet_id", walletId);
        body.put("category_code", categoryCode);
        body.put("occurred_at", Instant.now().toString());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.wallet.id").value(walletId));

        var wallet = walletRepository.findById(UUID.fromString(walletId)).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(500_000 - 68000);
    }

    @Test
    void createIncomeTransaction_addsWalletBalance_returns201() throws Exception {
        String token = registerAndGetAccessToken("tao.thu@example.com");
        String walletId = createWallet(token, "Ví Thu", 200_000);
        String categoryCode = leafCategoryCode();

        Map<String, Object> body = new HashMap<>();
        body.put("type", "INCOME");
        body.put("amount", 100000);
        body.put("wallet_id", walletId);
        body.put("category_code", categoryCode);
        body.put("occurred_at", Instant.now().toString());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("INCOME"));

        var wallet = walletRepository.findById(UUID.fromString(walletId)).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(200_000 + 100000);
    }

    @Test
    void createExpenseWithNonLeafCategory_returns400ValidationError() throws Exception {
        String token = registerAndGetAccessToken("parent.cate@example.com");
        String walletId = createWallet(token, "Ví Chi", 500_000);
        String parentCode = nonLeafCategoryCode();

        Map<String, Object> body = new HashMap<>();
        body.put("type", "EXPENSE");
        body.put("amount", 68000);
        body.put("wallet_id", walletId);
        body.put("category_code", parentCode);
        body.put("occurred_at", Instant.now().toString());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createTransactionWithFutureDate_isRejected_returns400() throws Exception {
        String token = registerAndGetAccessToken("ngay.tuong.lai@example.com");
        String walletId = createWallet(token, "Ví Test", 500_000);
        String categoryCode = leafCategoryCode();

        Map<String, Object> body = new HashMap<>();
        body.put("type", "INCOME");
        body.put("amount", 500000);
        body.put("wallet_id", walletId);
        body.put("category_code", categoryCode);
        body.put("occurred_at", Instant.now().plus(30, ChronoUnit.DAYS).toString());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
