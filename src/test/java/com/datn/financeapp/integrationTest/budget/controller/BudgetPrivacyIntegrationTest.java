package com.datn.financeapp.integrationTest.budget.controller;

import com.datn.financeapp.module.budget.controller.*;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.datn.financeapp.module.auth.repository.OtpRepository;
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

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * BUDGET-08 — test riêng tư ưu tiên cao nhất của Phase 4.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class BudgetPrivacyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1idWRnZXQtcHJpdmFjeS10ZXN0MzI=");
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

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    // Danh mục dùng chung cho mọi người dùng — cả A và B đều nhìn thấy và dùng chung.
    private String findSharedCategoryCode() {
        return jdbcTemplate.queryForObject(
                "SELECT code FROM categories WHERE is_deleted = FALSE ORDER BY code LIMIT 1",
                String.class);
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

    private void createExpenseTransaction(String token, String walletId, String categoryCode, long amount)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "EXPENSE");
        body.put("amount", amount);
        body.put("wallet_id", walletId);
        body.put("category_code", categoryCode);
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void userA_budgetSpentAmount_notAffectedByUserB_sameCategory() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.rieng.tu@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.rieng.tu@example.com");

        String sharedCategoryCode = findSharedCategoryCode();
        String budgetOfA = createBudget(tokenA, sharedCategoryCode, 3_000_000);

        // B chi 1 triệu vào đúng danh mục đó, trên ví của chính B.
        createExpenseTransaction(tokenB, firstWalletId(tokenB), sharedCategoryCode, 1_000_000);

        // Ngân sách CÁ NHÂN của A phải hoàn toàn không biết gì về khoản chi của B.
        mockMvc.perform(get("/budgets/" + budgetOfA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spent_amount").value(0))
                .andExpect(jsonPath("$.data.remaining").value(3_000_000))
                .andExpect(jsonPath("$.data.status").value("NORMAL"));

        // Và cũng không sinh cảnh báo nào cho A.
        mockMvc.perform(get("/budgets/alerts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Đối chứng: chính A chi 1 triệu thì con số PHẢI nhảy
        createExpenseTransaction(tokenA, firstWalletId(tokenA), sharedCategoryCode, 1_000_000);
        mockMvc.perform(get("/budgets/" + budgetOfA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spent_amount").value(1_000_000));
    }
}
