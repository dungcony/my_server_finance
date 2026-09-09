package com.datn.financeapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * JOB-02 (D-57, api/05-NGAN-SACH.md mục 8) — chống tạo trùng khi job {@code BudgetRenewalJob}
 * chạy lại. Gọi trực tiếp {@code budgetRenewalJob.run()} qua bean Spring (KHÔNG đợi cron thật).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetRenewalJobIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1idWRnZXQtcmVuZXdhbC1qb2ItdGVzdA==");
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
    private BudgetRenewalJob budgetRenewalJob;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM budgets");
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM wallets");
        jdbcTemplate.update("DELETE FROM categories WHERE user_id IS NOT NULL");
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "username", "Người Kiểm Thử Job Ngân Sách");
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) data.get("access_token");
    }

    private String findIconId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM icons WHERE code = ?", String.class, code);
    }

    private String findCategoryGroupId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM category_groups WHERE name = ?", String.class, name);
    }

    private String createRootCategory(String token, String name) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "expense",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "category_group_id", findCategoryGroupId("Khác"));
        String response = mockMvc.perform(post("/categories")
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

    private String createBudget(String token, String categoryId, long limitAmount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("category_id", categoryId);
        body.put("limit_amount", limitAmount);
        body.put("period_type", "month");
        body.put("auto_renew", true);
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

    // Đẩy end_date của ngân sách về hôm qua để mô phỏng kỳ đã hết hạn, đúng điều kiện JOB-02.
    private void expireYesterday(String budgetId) {
        jdbcTemplate.update(
                "UPDATE budgets SET start_date = start_date - INTERVAL '1 month', "
                        + "end_date = CURRENT_DATE - INTERVAL '1 day' WHERE id = ?",
                UUID.fromString(budgetId));
    }

    private int countActiveBudgets(String userToken, String categoryId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM budgets b JOIN users u ON u.id = b.user_id "
                        + "WHERE b.category_id = ? AND b.is_active = TRUE",
                Integer.class,
                UUID.fromString(categoryId));
    }

    // ---------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------

    /**
     * Ngân sách auto_renew=true đã hết kỳ (end_date hôm qua) — chạy job HAI LẦN liên tiếp, chỉ
     * ĐÚNG 1 ngân sách kỳ mới được tạo (chống tạo trùng qua existsOverlapping).
     */
    @Test
    void renewExpiredBudgets_calledTwice_createsOnlyOneNewPeriod() throws Exception {
        String token = registerAndGetAccessToken("gia.han.ngan.sach@example.com");
        String categoryId = createRootCategory(token, "Ăn uống gia hạn");
        String budgetId = createBudget(token, categoryId, 1_000_000);
        expireYesterday(budgetId);

        budgetRenewalJob.run();
        budgetRenewalJob.run();

        assertThat(countActiveBudgets(token, categoryId))
                .as("Chỉ đúng 1 ngân sách đang hiệu lực (kỳ mới) sau khi job chạy 2 lần")
                .isEqualTo(1);

        Integer totalBudgets = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM budgets WHERE category_id = ?", Integer.class, UUID.fromString(categoryId));
        assertThat(totalBudgets)
                .as("Tổng cộng đúng 2 bản ghi: kỳ cũ (đã tắt is_active) + kỳ mới")
                .isEqualTo(2);

        Integer renewalNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type = 'budget_renewed'", Integer.class);
        assertThat(renewalNotifications).as("Chỉ 1 thông báo lặp kỳ, không nhân đôi khi job chạy lại").isEqualTo(1);
    }
}
