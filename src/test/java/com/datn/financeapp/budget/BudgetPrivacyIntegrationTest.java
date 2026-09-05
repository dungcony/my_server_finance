package com.datn.financeapp.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
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
 * BUDGET-08 — test riêng tư ưu tiên cao nhất của Phase 4 (D-58).
 *
 * <p>Chốt VĨNH VIỄN lỗ hổng đã vá ở {@code V6__va_loi_bao_mat.sql}: bản {@code v_budget_progress}
 * gốc ở V3 cộng MỌI giao dịch thuộc cây danh mục, không hề giới hạn theo chủ sở hữu. Hậu quả là
 * hai người dùng bất kỳ dùng chung một danh mục HỆ THỐNG (đa số danh mục là hệ thống) sẽ thấy chi
 * tiêu của nhau lẫn vào ngân sách cá nhân mình — vi phạm nguyên tắc "riêng tư mặc định".
 *
 * <p>Lỗi thuộc loại nguy hiểm nhất: con số vẫn "trông hợp lý", không exception nào ném ra, không ai
 * nghi ngờ cho tới khi có người nhận ra ngân sách của mình tăng dù mình không tiêu gì.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "username", "Người Kiểm Thử Riêng Tư");
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

    /** Danh mục CHI hệ thống ({@code user_id IS NULL}) — cả A và B đều nhìn thấy và dùng chung. */
    private String findSharedSystemExpenseCategoryId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = 'expense' "
                        + "AND parent_category_id IS NULL AND NOT is_deleted ORDER BY sort_order LIMIT 1",
                String.class);
    }

    private String firstWalletId(String token) throws Exception {
        String response = mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        List<?> wallets = (List<?>) parsed.get("data");
        return (String) ((Map<?, ?>) wallets.get(0)).get("id");
    }

    private String createBudget(String token, String categoryId, long limitAmount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("category_id", categoryId);
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

    private void createExpenseTransaction(String token, String walletId, String categoryId, long amount)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "expense");
        body.put("amount", amount);
        body.put("wallet_id", walletId);
        body.put("category_id", categoryId);
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

        // Cùng một danh mục HỆ THỐNG — đây chính là điều kiện làm lộ lỗ hổng bản V3.
        String sharedCategoryId = findSharedSystemExpenseCategoryId();

        String budgetOfA = createBudget(tokenA, sharedCategoryId, 3_000_000);

        // B chi 1 triệu vào đúng danh mục đó, trên ví của chính B.
        createExpenseTransaction(tokenB, firstWalletId(tokenB), sharedCategoryId, 1_000_000);

        // Ngân sách CÁ NHÂN của A phải hoàn toàn không biết gì về khoản chi của B.
        mockMvc.perform(get("/budgets/" + budgetOfA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spent_amount").value(0))
                .andExpect(jsonPath("$.data.remaining").value(3_000_000))
                .andExpect(jsonPath("$.data.status").value("normal"));

        // Và cũng không sinh cảnh báo nào cho A.
        mockMvc.perform(get("/budgets/alerts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Đối chứng: chính A chi 1 triệu thì con số PHẢI nhảy — chứng minh test không "xanh giả"
        // do view trả 0 cho mọi trường hợp.
        createExpenseTransaction(tokenA, firstWalletId(tokenA), sharedCategoryId, 1_000_000);
        mockMvc.perform(get("/budgets/" + budgetOfA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spent_amount").value(1_000_000));
    }
}
