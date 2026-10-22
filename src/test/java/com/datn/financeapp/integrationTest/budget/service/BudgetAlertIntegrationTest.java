package com.datn.financeapp.integrationTest.budget.service;

import com.datn.financeapp.module.budget.service.*;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
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
 * Số đã chi (spent) và cảnh báo của ngân sách được cập nhật khi ghi giao dịch,
 * do BudgetTxnEventListener lắng nghe event từ TxnBehavierService.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class BudgetAlertIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1idWRnZXQtYWxlcnQtdGVzdDMyYg==");
    }

    @TestConfiguration
    static class NoRateLimitConfig {
        @Bean
        @Primary
        RateLimitFilter rateLimitFilter() {
            return new RateLimitFilter(null) {
                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
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
        jdbcTemplate.update("DELETE FROM budgets");
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM wallets");
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------- helpers

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private String seededRootCategoryCode() {
        return jdbcTemplate.queryForObject(
                "SELECT code FROM categories WHERE is_deleted = FALSE AND (parent_code IS NULL OR parent_code = '') ORDER BY code LIMIT 1",
                String.class);
    }

    private String seededChildCategoryCode(String parentCode) {
        return jdbcTemplate.queryForObject(
                "SELECT code FROM categories WHERE parent_code = ? AND is_deleted = FALSE ORDER BY code LIMIT 1",
                String.class,
                parentCode);
    }

    private String firstWalletId(String token) throws Exception {
        return authSupport.firstWalletId(token);
    }

    private String createBudget(String token, String categoryCode, long limitAmount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("category_code", categoryCode);
        body.put("limit_amount", limitAmount);
        body.put("period_type", "month");
        String response = mockMvc.perform(post("/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) data.get("id");
    }

    private Map<String, Object> expenseBody(String walletId, String categoryCode, long amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "EXPENSE");
        body.put("amount", amount);
        body.put("wallet_id", walletId);
        body.put("category_code", categoryCode);
        body.put("occurred_at", Instant.now().toString());
        return body;
    }

    private void createExpenseTransaction(String token, String walletId, String categoryCode, long amount)
            throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expenseBody(walletId, categoryCode, amount))))
                .andExpect(status().isCreated());
    }

    private int countBudgetAlerts(String budgetId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE reference_id = ? AND type = 'BUDGET_ALERT'",
                Integer.class,
                UUID.fromString(budgetId));
        return count == null ? 0 : count;
    }

    private long spentOf(String budgetId) {
        Long spent = jdbcTemplate.queryForObject(
                "SELECT spent FROM budgets WHERE id = ?", Long.class, UUID.fromString(budgetId));
        return spent == null ? 0L : spent;
    }

    private String onlyTransactionIdIn(String categoryCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM transactions WHERE category_code = ?", String.class, categoryCode);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void expenseExceedsBudget_createsNotification() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.vuot@example.com");
        String categoryCode = seededRootCategoryCode();
        String budgetId = createBudget(token, categoryCode, 1_000_000);

        Assertions.assertEquals(0, countBudgetAlerts(budgetId));

        createExpenseTransaction(token, firstWalletId(token), categoryCode, 1_200_000);

        Assertions.assertEquals(
                1, countBudgetAlerts(budgetId), "Chi vượt hạn mức phải sinh đúng 1 cảnh báo ngân sách");

        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM notifications WHERE reference_id = ? AND type = 'BUDGET_ALERT'",
                String.class,
                UUID.fromString(budgetId));
        Assertions.assertEquals("Đã vượt ngân sách", title);
    }

    @Test
    void expenseInChildCategory_triggersAlertOfParentCategoryBudget() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.danh.muc.con@example.com");
        String parentCode = seededRootCategoryCode();
        String childCode = seededChildCategoryCode(parentCode);
        String budgetId = createBudget(token, parentCode, 1_000_000);

        createExpenseTransaction(token, firstWalletId(token), childCode, 1_500_000);

        Assertions.assertEquals(1_500_000L, spentOf(budgetId), "Chi ở danh mục CON phải cộng vào ngân sách CHA");
        Assertions.assertEquals(
                1,
                countBudgetAlerts(budgetId),
                "Chi vào danh mục CON phải kích hoạt ngân sách đặt ở danh mục CHA");
    }

    @Test
    void bulkFiftyRows_sameBudgetSameDay_createsOnlyOneNotification() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.bulk@example.com");
        String categoryCode = seededRootCategoryCode();
        String budgetId = createBudget(token, categoryCode, 10_000_000);
        String walletId = firstWalletId(token);

        for (int i = 0; i < 50; i++) {
            createExpenseTransaction(token, walletId, categoryCode, 300_000);
        }

        Assertions.assertEquals(15_000_000L, spentOf(budgetId), "50 lần cộng nguyên tử không được mất lần nào");
        Assertions.assertEquals(
                1,
                countBudgetAlerts(budgetId),
                "50 giao dịch cùng ngân sách trong cùng ngày chỉ được sinh ĐÚNG 1 cảnh báo");
    }

    @Test
    void updateExpenseAmount_revertsOldThenAppliesNew() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.sua@example.com");
        String categoryCode = seededRootCategoryCode();
        String budgetId = createBudget(token, categoryCode, 10_000_000);
        String walletId = firstWalletId(token);

        createExpenseTransaction(token, walletId, categoryCode, 400_000);
        Assertions.assertEquals(400_000L, spentOf(budgetId));

        Map<String, Object> updateReq = new HashMap<>();
        updateReq.put("amount", 250_000);
        updateReq.put("type", "EXPENSE");
        updateReq.put("category_code", categoryCode);

        mockMvc.perform(patch("/transactions/" + onlyTransactionIdIn(categoryCode))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        Assertions.assertEquals(250_000L, spentOf(budgetId));
    }

    @Test
    void deleteExpense_revertsSpent() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.xoa@example.com");
        String categoryCode = seededRootCategoryCode();
        String budgetId = createBudget(token, categoryCode, 10_000_000);

        createExpenseTransaction(token, firstWalletId(token), categoryCode, 400_000);
        Assertions.assertEquals(400_000L, spentOf(budgetId));

        mockMvc.perform(delete("/transactions/" + onlyTransactionIdIn(categoryCode))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Assertions.assertEquals(0L, spentOf(budgetId));
    }
}
