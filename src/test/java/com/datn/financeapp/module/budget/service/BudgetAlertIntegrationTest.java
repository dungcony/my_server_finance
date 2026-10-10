package com.datn.financeapp.module.budget.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.ArrayList;
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
 * Số đã chi ({@code spent}) và cảnh báo của ngân sách được cập nhật ĐỒNG BỘ trong transaction ghi giao
 * dịch, do {@code BudgetSpendService.addExpense} gọi từ {@code TransactionWriter} / {@code TransactionService}.
 * Cảnh báo vào bảng {@code notifications}. Gọi qua MockMvc request thật nên Spring quản lý transaction
 * y như production; danh mục lấy từ dữ liệu seed.
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
        // OTP nằm ở Redis, không bị Testcontainers PostgreSQL dọn hộ.
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

    // Một danh mục gốc dùng chung, lấy từ dữ liệu seed.
    private String seededRootCategoryId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE parent_id IS NULL AND deleted = FALSE ORDER BY name LIMIT 1",
                String.class);
    }

    // Một danh mục con của danh mục cho trước, lấy từ dữ liệu seed.
    private String seededChildCategoryId(String parentId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE parent_id = ?::uuid AND deleted = FALSE ORDER BY name LIMIT 1",
                String.class,
                parentId);
    }

    private String firstWalletId(String token) throws Exception {
        return authSupport.firstWalletId(token);
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

    private Map<String, Object> expenseBody(String walletId, String categoryId, long amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "expense");
        body.put("amount", amount);
        body.put("wallet_id", walletId);
        body.put("category_id", categoryId);
        return body;
    }

    private void createExpenseTransaction(String token, String walletId, String categoryId, long amount)
            throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expenseBody(walletId, categoryId, amount))))
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

    private String onlyTransactionIdIn(String categoryId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM transactions WHERE category_id = ?::uuid", String.class, categoryId);
    }

    // ------------------------------------------------------------------ tests

    // Ghi một khoản chi vượt hạn mức phải sinh đúng một bản ghi cảnh báo.
    @Test
    void expenseExceedsBudget_createsNotification() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.vuot@example.com");
        String categoryId = seededRootCategoryId();
        String budgetId = createBudget(token, categoryId, 1_000_000);

        // Trước khi chi: chưa có cảnh báo nào.
        Assertions.assertEquals(0, countBudgetAlerts(budgetId));

        createExpenseTransaction(token, firstWalletId(token), categoryId, 1_200_000);

        Assertions.assertEquals(
                1, countBudgetAlerts(budgetId), "Chi vượt hạn mức phải sinh đúng 1 cảnh báo ngân sách");

        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM notifications WHERE reference_id = ? AND type = 'BUDGET_ALERT'",
                String.class,
                UUID.fromString(budgetId));
        Assertions.assertEquals("Đã vượt ngân sách", title);
    }

    /**
     * Ghi vào danh mục CON phải cộng và kích hoạt cả ngân sách đặt ở danh mục CHA (CLAUDE.md §1). Đây
     * là ca mà so sánh {@code categoryId} bằng nhau sẽ bỏ sót — cảnh báo im lặng không bao giờ bắn,
     * và không có gì báo lỗi.
     */
    @Test
    void expenseInChildCategory_triggersAlertOfParentCategoryBudget() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.danh.muc.con@example.com");
        String parentId = seededRootCategoryId();
        String childId = seededChildCategoryId(parentId);
        String budgetId = createBudget(token, parentId, 1_000_000);

        createExpenseTransaction(token, firstWalletId(token), childId, 1_500_000);

        Assertions.assertEquals(1_500_000L, spentOf(budgetId), "Chi ở danh mục CON phải cộng vào ngân sách CHA");
        Assertions.assertEquals(
                1,
                countBudgetAlerts(budgetId),
                "Chi vào danh mục CON phải kích hoạt ngân sách đặt ở danh mục CHA");
    }

    /**
     * Bulk 50 dòng = 50 lần {@code addExpense} cùng một ngân sách, nhưng chống trùng nằm ở UNIQUE
     * tầng CSDL {@code uq_notif_budget_alert} (user, reference, ngày, loại) chứ không phải ở kiểm
     * tra tồn tại trong Java — nên kết quả phải là ĐÚNG 1 bản ghi, không phải 50.
     */
    @Test
    void bulkFiftyRows_sameBudgetSameDay_createsOnlyOneNotification() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.bulk@example.com");
        String categoryId = seededRootCategoryId();
        String budgetId = createBudget(token, categoryId, 10_000_000);
        String walletId = firstWalletId(token);

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            items.add(expenseBody(walletId, categoryId, 300_000));
        }

        mockMvc.perform(post("/transactions/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isCreated());

        // 50 × 300.000 = 15.000.000 > hạn mức 10.000.000 -> chắc chắn vượt ngưỡng.
        Assertions.assertEquals(15_000_000L, spentOf(budgetId), "50 lần cộng nguyên tử không được mất lần nào");
        Assertions.assertEquals(
                1,
                countBudgetAlerts(budgetId),
                "Bulk 50 dòng cùng ngân sách trong cùng ngày chỉ được sinh ĐÚNG 1 cảnh báo");
    }

    /**
     * Sửa số tiền phải hoàn tác khoản cũ rồi áp dụng khoản mới (quy tắc nghiệp vụ 4): 400k → 250k thì
     * {@code spent} chỉ còn 250k, không phải 650k hay 400k.
     */
    @Test
    void updateExpenseAmount_revertsOldThenAppliesNew() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.sua@example.com");
        String categoryId = seededRootCategoryId();
        String budgetId = createBudget(token, categoryId, 10_000_000);
        String walletId = firstWalletId(token);

        createExpenseTransaction(token, walletId, categoryId, 400_000);
        Assertions.assertEquals(400_000L, spentOf(budgetId));

        mockMvc.perform(put("/transactions/" + onlyTransactionIdIn(categoryId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expenseBody(walletId, categoryId, 250_000))))
                .andExpect(status().isOk());

        Assertions.assertEquals(250_000L, spentOf(budgetId));
    }

    // Xoá giao dịch phải trả lại phần đã cộng vào ngân sách.
    @Test
    void deleteExpense_revertsSpent() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.xoa@example.com");
        String categoryId = seededRootCategoryId();
        String budgetId = createBudget(token, categoryId, 10_000_000);

        createExpenseTransaction(token, firstWalletId(token), categoryId, 400_000);
        Assertions.assertEquals(400_000L, spentOf(budgetId));

        mockMvc.perform(delete("/transactions/" + onlyTransactionIdIn(categoryId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Assertions.assertEquals(0L, spentOf(budgetId));
    }
}
