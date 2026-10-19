package com.datn.financeapp.integrationTest.transaction.controller;

import com.datn.financeapp.module.transaction.controller.*;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.time.LocalDate;
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

/**
 * Test tích hợp cho affected_budgets trong response POST /transactions.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class TransactionAffectedBudgetsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hZmZlY3RlZC1idWRnZXQtMzJi");
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
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM budgets");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private UUID firstWalletId(String token) throws Exception {
        return UUID.fromString(authSupport.firstWalletId(token));
    }

    private UUID userIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private String systemCategoryCode(String name, String type) {
        return jdbcTemplate.queryForObject(
                "SELECT code FROM categories WHERE is_deleted = FALSE AND type = ? AND name = ? LIMIT 1",
                String.class, type, name);
    }

    private void createBudget(UUID userId, String categoryCode, long limitAmount) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1);
        jdbcTemplate.update(
                "INSERT INTO budgets (id, user_id, category_code, limit_amount, period_type, start_date, end_date, auto_renew, is_active, spent) "
                        + "VALUES (?, ?, ?, ?, 'MONTH', ?, ?, TRUE, TRUE, 0)",
                UUID.randomUUID(), userId, categoryCode, limitAmount, start, end);
    }

    private Map<String, Object> postTransaction(String token, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        return (Map<String, Object>) parsed.get("data");
    }

    @Test
    void expenseIntoChildCategory_budgetOverLimit_returnsAffectedBudgetWithRootCategoryName() throws Exception {
        String token = registerAndGetAccessToken("affected.budget.1@example.com");
        UUID userId = userIdOf("affected.budget.1@example.com");
        UUID walletId = firstWalletId(token);
        String anUongCode = systemCategoryCode("Ăn uống", "EXPENSE");
        String caPheCode = systemCategoryCode("Cà phê", "EXPENSE");

        createBudget(userId, anUongCode, 1_000_000);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "EXPENSE");
        body.put("amount", 900_000);
        body.put("wallet_id", walletId.toString());
        body.put("category_code", caPheCode);
        body.put("occurred_at", Instant.now().toString());

        Map<String, Object> data = postTransaction(token, body);
        var affectedBudgets = (java.util.List<Map<String, Object>>) data.get("affected_budgets");

        org.assertj.core.api.Assertions.assertThat(affectedBudgets).hasSize(1);
        Map<String, Object> affected = affectedBudgets.get(0);
        Object catName = affected.get("category_name") != null ? affected.get("category_name") : affected.get("category");
        org.assertj.core.api.Assertions.assertThat(catName).isEqualTo("Ăn uống");
        org.assertj.core.api.Assertions.assertThat(affected.get("status").toString().toLowerCase()).isIn("near_limit", "over_limit");
    }

    @Test
    void incomeTransaction_alwaysReturnsEmptyAffectedBudgets() throws Exception {
        String token = registerAndGetAccessToken("affected.budget.2@example.com");
        UUID userId = userIdOf("affected.budget.2@example.com");
        UUID walletId = firstWalletId(token);
        String anUongCode = systemCategoryCode("Ăn uống", "EXPENSE");
        String thuNhapCode = systemCategoryCode("Lương", "INCOME");

        createBudget(userId, anUongCode, 100);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "INCOME");
        body.put("amount", 5_000_000);
        body.put("wallet_id", walletId.toString());
        body.put("category_code", thuNhapCode);
        body.put("occurred_at", Instant.now().toString());

        Map<String, Object> data = postTransaction(token, body);
        var affectedBudgets = (java.util.List<Map<String, Object>>) data.get("affected_budgets");

        org.assertj.core.api.Assertions.assertThat(affectedBudgets).isEmpty();
    }

    @Test
    void expenseWithinNormalBudget_notReturnedInAffectedBudgets() throws Exception {
        String token = registerAndGetAccessToken("affected.budget.3@example.com");
        UUID userId = userIdOf("affected.budget.3@example.com");
        UUID walletId = firstWalletId(token);
        String anUongCode = systemCategoryCode("Ăn uống", "EXPENSE");
        String caPheCode = systemCategoryCode("Cà phê", "EXPENSE");

        createBudget(userId, anUongCode, 100_000_000);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "EXPENSE");
        body.put("amount", 10_000);
        body.put("wallet_id", walletId.toString());
        body.put("category_code", caPheCode);
        body.put("occurred_at", Instant.now().toString());

        Map<String, Object> data = postTransaction(token, body);
        var affectedBudgets = (java.util.List<Map<String, Object>>) data.get("affected_budgets");

        org.assertj.core.api.Assertions.assertThat(affectedBudgets).isEmpty();
    }
}
