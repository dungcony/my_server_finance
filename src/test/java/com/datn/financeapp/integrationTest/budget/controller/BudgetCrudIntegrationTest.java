package com.datn.financeapp.integrationTest.budget.controller;

import com.datn.financeapp.module.budget.controller.*;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import java.util.HashMap;
import java.util.List;
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
 * CRUD ngân sách. Ba luồng rủi ro: chặn trùng kỳ, chặn sửa danh mục/kỳ, và xoá mềm.
 * Danh mục lấy từ dữ liệu seed.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class BudgetCrudIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1idWRnZXQtY3J1ZC10ZXN0LTMyYg==");
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

    // Hai danh mục gốc khác nhau lấy từ dữ liệu seed.
    private List<String> seededCategoryCodes() {
        return jdbcTemplate.queryForList(
                "SELECT code FROM categories WHERE is_deleted = FALSE ORDER BY code LIMIT 2",
                String.class);
    }

    private String createBudget(String token, String categoryCode, long limitAmount, String periodType)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("category_code", categoryCode);
        body.put("limit_amount", limitAmount);
        body.put("period_type", periodType);
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

    // ------------------------------------------------------------------ tests

    @Test
    void createBudget_duplicatePeriod_returns409() throws Exception {
        String token = registerAndGetAccessToken("ngan.sach.trung@example.com");
        String categoryCode = seededCategoryCodes().get(0);

        createBudget(token, categoryCode, 3_000_000, "month");

        Map<String, Object> duplicate = new HashMap<>();
        duplicate.put("category_code", categoryCode);
        duplicate.put("limit_amount", 5_000_000);
        duplicate.put("period_type", "month");

        mockMvc.perform(post("/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUDGET_ALREADY_EXISTS"));
    }

    @Test
    void updateBudget_changeCategoryId_returns400CategoryNotEditable() throws Exception {
        String token = registerAndGetAccessToken("ngan.sach.doi.danh.muc@example.com");
        String categoryCode = seededCategoryCodes().get(0);
        String otherCategoryCode = seededCategoryCodes().get(1);
        String budgetId = createBudget(token, categoryCode, 3_000_000, "month");

        mockMvc.perform(patch("/budgets/" + budgetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("category_code", otherCategoryCode))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUDGET_CATEGORY_NOT_EDITABLE"));

        mockMvc.perform(patch("/budgets/" + budgetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("period_type", "week"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUDGET_CATEGORY_NOT_EDITABLE"));
    }

    @Test
    void deleteBudget_setsIsActiveFalse_notPhysicalDelete() throws Exception {
        String token = registerAndGetAccessToken("ngan.sach.xoa.mem@example.com");
        String categoryCode = seededCategoryCodes().get(0);
        String budgetId = createBudget(token, categoryCode, 2_000_000, "month");

        mockMvc.perform(delete("/budgets/" + budgetId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM budgets WHERE id = ?", Integer.class, UUID.fromString(budgetId));
        Assertions.assertEquals(1, rowCount, "Xoá mềm KHÔNG được xoá bản ghi khỏi CSDL");

        Boolean isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM budgets WHERE id = ?", Boolean.class, UUID.fromString(budgetId));
        Assertions.assertEquals(Boolean.FALSE, isActive, "Xoá mềm phải tắt is_active");

        mockMvc.perform(get("/budgets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        createBudget(token, categoryCode, 2_500_000, "month");
    }
}
