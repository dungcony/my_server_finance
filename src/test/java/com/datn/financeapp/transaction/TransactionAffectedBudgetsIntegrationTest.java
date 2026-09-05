package com.datn.financeapp.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * Test tích hợp cho {@code affected_budgets} trong response {@code POST /transactions} (Task 3,
 * plan 06-01) — api/04-GIAO-DICH.md mục 4, dòng 249-262. Trước khi sửa {@code TransactionService},
 * {@code affected_budgets} luôn rỗng bất kể ngân sách đang gần/vượt hạn mức (RED).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM budgets");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "username", "Người Kiểm Thử Ngân Sách");
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("access_token");
    }

    private UUID firstWalletId(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM wallets WHERE user_id = ? AND NOT is_deleted LIMIT 1", UUID.class, userId);
    }

    private UUID userIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private UUID systemCategoryId(String name, String type) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = ? AND name = ? LIMIT 1",
                UUID.class, type, name);
    }

    private void createBudget(UUID userId, UUID categoryId, long limitAmount) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        jdbcTemplate.update(
                "INSERT INTO budgets (user_id, category_id, limit_amount, period_type, start_date, end_date, auto_renew, is_active) "
                        + "VALUES (?, ?, ?, 'month', ?, ?, TRUE, TRUE)",
                userId, categoryId, limitAmount, start, end);
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
        UUID walletId = firstWalletId(userId);
        UUID anUongId = systemCategoryId("Ăn uống", "expense");
        UUID caPheId = systemCategoryId("Cà phê", "expense");

        // Ngân sách 1.000.000đ cho "Ăn uống" (cha) — chi 900.000đ vào "Cà phê" (con) => 90% => near_limit/over_limit.
        createBudget(userId, anUongId, 1_000_000);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "expense");
        body.put("amount", 900_000);
        body.put("wallet_id", walletId.toString());
        body.put("category_id", caPheId.toString());

        Map<String, Object> data = postTransaction(token, body);
        var affectedBudgets = (java.util.List<Map<String, Object>>) data.get("affected_budgets");

        org.assertj.core.api.Assertions.assertThat(affectedBudgets).hasSize(1);
        Map<String, Object> affected = affectedBudgets.get(0);
        org.assertj.core.api.Assertions.assertThat(affected.get("category")).isEqualTo("Ăn uống");
        org.assertj.core.api.Assertions.assertThat(affected.get("status")).isIn("near_limit", "over_limit");
    }

    @Test
    void incomeTransaction_alwaysReturnsEmptyAffectedBudgets() throws Exception {
        String token = registerAndGetAccessToken("affected.budget.2@example.com");
        UUID userId = userIdOf("affected.budget.2@example.com");
        UUID walletId = firstWalletId(userId);
        UUID anUongId = systemCategoryId("Ăn uống", "expense");
        UUID thuNhapId = systemCategoryId("Lương", "income");

        createBudget(userId, anUongId, 100);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "income");
        body.put("amount", 5_000_000);
        body.put("wallet_id", walletId.toString());
        body.put("category_id", thuNhapId.toString());

        Map<String, Object> data = postTransaction(token, body);
        var affectedBudgets = (java.util.List<Map<String, Object>>) data.get("affected_budgets");

        org.assertj.core.api.Assertions.assertThat(affectedBudgets).isEmpty();
    }

    @Test
    void expenseWithinNormalBudget_notReturnedInAffectedBudgets() throws Exception {
        String token = registerAndGetAccessToken("affected.budget.3@example.com");
        UUID userId = userIdOf("affected.budget.3@example.com");
        UUID walletId = firstWalletId(userId);
        UUID anUongId = systemCategoryId("Ăn uống", "expense");
        UUID caPheId = systemCategoryId("Cà phê", "expense");

        // Ngân sách rất lớn so với khoản chi -> status vẫn "normal" -> KHÔNG xuất hiện.
        createBudget(userId, anUongId, 100_000_000);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "expense");
        body.put("amount", 10_000);
        body.put("wallet_id", walletId.toString());
        body.put("category_id", caPheId.toString());

        Map<String, Object> data = postTransaction(token, body);
        var affectedBudgets = (java.util.List<Map<String, Object>>) data.get("affected_budgets");

        org.assertj.core.api.Assertions.assertThat(affectedBudgets).isEmpty();
    }
}
