package com.datn.financeapp.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 4 test case cho REPORT-01/02/04 (api/06-BAO-CAO.md) — Task 2 plan 04-06. Ưu tiên D-58 mục 5:
 * loại transfer + adjustment không tính báo cáo, cộng gộp danh mục con, home gộp đủ khối trong
 * MỘT lần gọi, và daily-trend current_line chỉ chạy tới hôm nay.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportSummaryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1yZXBvcnQtdGVzdC0zMmI=");
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
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM categories WHERE user_id IS NOT NULL");
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------- helpers

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", "matkhau123", "full_name", "Người Kiểm Thử Báo Cáo");
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

    private String createWallet(String token, String name, long initialBalance) throws Exception {
        Map<String, Object> body = Map.of("name", name, "type", "cash", "initial_balance", initialBalance);
        String response = mockMvc.perform(post("/wallets")
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

    private String findIconId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM icons WHERE code = ?", String.class, code);
    }

    private String findCategoryGroupId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM category_groups WHERE name = ?", String.class, name);
    }

    private String createCategory(String token, String name, String type, String parentId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("type", type);
        body.put("icon_id", findIconId("khac"));
        body.put("color", "#3d6b7d");
        body.put("category_group_id", findCategoryGroupId("Khác"));
        if (parentId != null) {
            body.put("parent_category_id", parentId);
        }
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

    // ------------------------------------------------------------------ tests

    /**
     * REPORT-01, D-58 mục 5: một giao dịch expense thường, một transfer, một expense
     * {@code counts_in_report=false} (mô phỏng adjustment) — chỉ giao dịch expense thường được
     * tính vào {@code total_expense}.
     */
    @Test
    void summary_excludesTransferAndNonReportingAdjustment() throws Exception {
        String token = registerAndGetAccessToken("bao.cao.tong.quan@example.com");
        String walletA = createWallet(token, "Ví A", 5_000_000);
        String walletB = createWallet(token, "Ví B", 1_000_000);
        String categoryId = createCategory(token, "Ăn uống báo cáo", "expense", null);

        createExpenseTransaction(token, walletA, categoryId, 100_000);

        Map<String, Object> transferBody = new HashMap<>();
        transferBody.put("type", "transfer");
        transferBody.put("amount", 2_000_000);
        transferBody.put("wallet_id", walletA);
        transferBody.put("destination_wallet_id", walletB);
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferBody)))
                .andExpect(status().isCreated());

        Map<String, Object> adjustmentBody = new HashMap<>();
        adjustmentBody.put("type", "expense");
        adjustmentBody.put("amount", 50_000);
        adjustmentBody.put("wallet_id", walletA);
        adjustmentBody.put("category_id", categoryId);
        adjustmentBody.put("counts_in_report", false);
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjustmentBody)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/reports/summary")
                        .param("period", "month")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_expense").value(100_000))
                .andExpect(jsonPath("$.data.total_income").value(0));
    }

    /** REPORT-01 — cộng gộp danh mục con: giao dịch gán vào con phải cộng vào cha ở by-category. */
    @Test
    void byCategory_parentLevel_aggregatesChildTransactions() throws Exception {
        String token = registerAndGetAccessToken("bao.cao.danh.muc.con@example.com");
        String walletId = createWallet(token, "Ví Ăn Uống", 3_000_000);
        String parentId = createCategory(token, "Ăn uống cha", "expense", null);
        String childId = createCategory(token, "Cà phê con", "expense", parentId);

        createExpenseTransaction(token, walletId, childId, 68_000);
        createExpenseTransaction(token, walletId, parentId, 42_000);

        mockMvc.perform(get("/reports/by-category")
                        .param("period", "month")
                        .param("level", "parent")
                        .param("type", "expense")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].category_id").value(parentId))
                .andExpect(jsonPath("$.data.items[0].amount").value(68_000 + 42_000))
                .andExpect(jsonPath("$.data.items[0].has_children").value(true));
    }

    /** REPORT-02 — home gộp đủ các khối bắt buộc trong MỘT lần gọi (api/06 mục 1). */
    @Test
    void home_returnsAllRequiredBlocks_inOneCall() throws Exception {
        String token = registerAndGetAccessToken("bao.cao.trang.chu@example.com");
        String walletId = createWallet(token, "Ví Tổng Quan", 2_000_000);
        String categoryId = createCategory(token, "Đi lại báo cáo", "expense", null);
        createExpenseTransaction(token, walletId, categoryId, 42_000);

        mockMvc.perform(get("/reports/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").exists())
                .andExpect(jsonPath("$.data.top_wallets").exists())
                .andExpect(jsonPath("$.data.period_summary").exists())
                .andExpect(jsonPath("$.data.daily_trend").exists())
                .andExpect(jsonPath("$.data.top_spending").exists())
                .andExpect(jsonPath("$.data.recent_transactions").exists())
                .andExpect(jsonPath("$.data.recent_transactions[0].amount").value(42_000));
    }

    /** REPORT-04 — current_line CHỈ có điểm dữ liệu tới hôm nay, không kéo dài hết tháng. */
    @Test
    void dailyTrend_currentLine_stopsAtToday() throws Exception {
        String token = registerAndGetAccessToken("bao.cao.xu.huong.ngay@example.com");
        String walletId = createWallet(token, "Ví Xu Hướng", 1_000_000);
        String categoryId = createCategory(token, "Mua sắm báo cáo", "expense", null);
        createExpenseTransaction(token, walletId, categoryId, 30_000);

        String response = mockMvc.perform(get("/reports/daily-trend").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        var currentLine = (java.util.List<?>) data.get("current_line");
        int today = java.time.LocalDate.now().getDayOfMonth();
        for (Object point : currentLine) {
            int date = (Integer) ((Map<?, ?>) point).get("date");
            org.assertj.core.api.Assertions.assertThat(date).isLessThanOrEqualTo(today);
        }
    }
}
