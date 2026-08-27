package com.datn.financeapp.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * D-41 — cảnh báo ngân sách sinh tự động vào bảng {@code notifications} qua
 * {@code BudgetAlertListener} với {@code @TransactionalEventListener(AFTER_COMMIT)}.
 *
 * <p><b>Class này CỐ Ý KHÔNG có {@code @Transactional}</b> ở cấp class hay method. Test JPA có
 * {@code @Transactional} sẽ tự rollback thay vì commit, nên listener {@code AFTER_COMMIT} không
 * bao giờ chạy và test sẽ đỏ vì lý do hoàn toàn không liên quan tới nghiệp vụ. Gọi qua MockMvc
 * request thật thì Spring quản lý transaction theo request y như production — listener chạy đúng
 * lúc, không cần {@code @Commit} hay {@code TestTransaction} đặc biệt. (Cùng khuôn với
 * {@code WalletTransferConcurrencyTest}, cũng không dùng {@code @Transactional} cấp class.)
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetAlertListenerIntegrationTest {

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

    // ---------------------------------------------------------------- helpers

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "full_name", "Người Kiểm Thử Cảnh Báo");
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

    private String createChildCategory(String token, String name, String parentId) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "expense",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "parent_category_id", parentId);
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

    private String firstWalletId(String token) throws Exception {
        String response = mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<?> wallets = (List<?>) objectMapper.readValue(response, Map.class).get("data");
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

    private int countBudgetAlerts(String budgetId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE reference_id = ? AND type = 'budget_alert'",
                Integer.class,
                UUID.fromString(budgetId));
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------ tests

    /** Ghi một khoản chi vượt hạn mức phải sinh đúng một bản ghi cảnh báo SAU KHI transaction commit. */
    @Test
    void expenseExceedsBudget_createsNotificationAfterCommit() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.vuot@example.com");
        String categoryId = createRootCategory(token, "Ăn uống test");
        String budgetId = createBudget(token, categoryId, 1_000_000);

        // Trước khi chi: chưa có cảnh báo nào.
        Assertions.assertEquals(0, countBudgetAlerts(budgetId));

        createExpenseTransaction(token, firstWalletId(token), categoryId, 1_200_000);

        Assertions.assertEquals(
                1, countBudgetAlerts(budgetId), "Chi vượt hạn mức phải sinh đúng 1 cảnh báo ngân sách");

        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM notifications WHERE reference_id = ? AND type = 'budget_alert'",
                String.class,
                UUID.fromString(budgetId));
        Assertions.assertEquals("Đã vượt ngân sách", title);
    }

    /**
     * Ghi vào danh mục CON phải kích hoạt cả ngân sách đặt ở danh mục CHA (CLAUDE.md §1). Đây là
     * ca mà lọc bằng so sánh {@code categoryId} bằng nhau ở Java sẽ bỏ sót — cảnh báo im lặng
     * không bao giờ bắn, và không có gì báo lỗi.
     */
    @Test
    void expenseInChildCategory_triggersAlertOfParentCategoryBudget() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.danh.muc.con@example.com");
        String parentId = createRootCategory(token, "Ăn uống test");
        String childId = createChildCategory(token, "Cà phê test", parentId);
        String budgetId = createBudget(token, parentId, 1_000_000);

        createExpenseTransaction(token, firstWalletId(token), childId, 1_500_000);

        Assertions.assertEquals(
                1,
                countBudgetAlerts(budgetId),
                "Chi vào danh mục CON phải kích hoạt ngân sách đặt ở danh mục CHA");
    }

    /**
     * D-34/D-41: bulk 50 dòng = 50 transaction riêng = 50 event, nhưng chống trùng nằm ở UNIQUE
     * tầng CSDL {@code uq_notif_budget_alert} (user, reference, ngày, loại) chứ không phải ở kiểm
     * tra tồn tại trong Java — nên kết quả phải là ĐÚNG 1 bản ghi, không phải 50.
     */
    @Test
    void bulkFiftyRows_sameBudgetSameDay_createsOnlyOneNotification() throws Exception {
        String token = registerAndGetAccessToken("canh.bao.bulk@example.com");
        String categoryId = createRootCategory(token, "Mua sắm test");
        String budgetId = createBudget(token, categoryId, 10_000_000);
        String walletId = firstWalletId(token);

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("type", "expense");
            item.put("amount", 300_000);
            item.put("wallet_id", walletId);
            item.put("category_id", categoryId);
            items.add(item);
        }

        mockMvc.perform(post("/transactions/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isCreated());

        // 50 × 300.000 = 15.000.000 > hạn mức 10.000.000 -> chắc chắn vượt ngưỡng.
        Assertions.assertEquals(
                1,
                countBudgetAlerts(budgetId),
                "Bulk 50 dòng cùng ngân sách trong cùng ngày chỉ được sinh ĐÚNG 1 cảnh báo");
    }
}
