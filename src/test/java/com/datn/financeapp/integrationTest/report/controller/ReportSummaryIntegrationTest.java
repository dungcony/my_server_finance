package com.datn.financeapp.integrationTest.report.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
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
 * Integration test cho các điểm cuối truy vấn báo cáo (/reports).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
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

    @Autowired
    private TestAuthSupport authSupport;

    @Autowired
    private OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM export_jobs");
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------- helpers

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private String createWallet(String token, String name, long initialBalance) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "CASH",
                "balance", initialBalance,
                "iconCode", "default_wallet",
                "color", "#3d6b7d");
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

    private void createExpenseTransaction(String token, String walletId, String categoryCode, long amount)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "EXPENSE");
        body.put("amount", amount);
        body.put("walletId", walletId);
        body.put("categoryCode", categoryCode);
        body.put("occurredAt", Instant.now().toString());
        body.put("displayName", "Giao dịch test");
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ tests

    @Test
    void summary_calculatesTotalExpenseAccurately() throws Exception {
        String token = registerAndGetAccessToken("bao.cao.tong.quan@example.com");
        String walletA = createWallet(token, "Ví A", 5_000_000);

        createExpenseTransaction(token, walletA, "an_sang", 100_000);

        mockMvc.perform(get("/reports/summary")
                        .param("period", "month")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_expense").value(100_000));
    }

    @Test
    void byCategory_parentLevel_aggregatesChildTransactions() throws Exception {
        String token = registerAndGetAccessToken("bao.cao.danh.muc.con@example.com");
        String walletId = createWallet(token, "Ví Ăn Uống", 3_000_000);

        createExpenseTransaction(token, walletId, "ca_phe", 68_000);
        createExpenseTransaction(token, walletId, "an_sang", 42_000);

        mockMvc.perform(get("/reports/by-category")
                        .param("period", "month")
                        .param("level", "parent")
                        .param("type", "expense")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].code").value("an_uong"))
                .andExpect(jsonPath("$.data.items[0].amount").value(68_000 + 42_000))
                .andExpect(jsonPath("$.data.items[0].has_children").value(true));
    }

    @Test
    void home_returnsAllRequiredBlocks_inOneCall() throws Exception {
        String token = registerAndGetAccessToken("bao.cao.trang.chu@example.com");
        String walletId = createWallet(token, "Ví Tổng Quan", 2_000_000);
        createExpenseTransaction(token, walletId, "xang_xe", 42_000);

        mockMvc.perform(get("/reports/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").exists())
                .andExpect(jsonPath("$.data.top_wallets").exists())
                .andExpect(jsonPath("$.data.period_summary").exists())
                .andExpect(jsonPath("$.data.daily_trend").exists())
                .andExpect(jsonPath("$.data.top_spending").exists())
                .andExpect(jsonPath("$.data.recent_transactions").exists())
                .andExpect(jsonPath("$.data.recent_transactions[0].amount").value(42_000))
                .andExpect(jsonPath("$.data.budgets_needing_attention").exists());
    }

    @Test
    void dailyTrend_currentLine_stopsAtToday() throws Exception {
        String token = registerAndGetAccessToken("bao.cao.xu.huong.ngay@example.com");
        String walletId = createWallet(token, "Ví Xu Hướng", 1_000_000);
        createExpenseTransaction(token, walletId, "quan_ao", 30_000);

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

    @Test
    void reports_locTheoViTrenMoiDiemCuoi() throws Exception {
        String token = registerAndGetAccessToken("loc.theo.vi@example.com");
        String walletA = createWallet(token, "Ví A lọc", 5_000_000);
        String walletB = createWallet(token, "Ví B lọc", 5_000_000);

        createExpenseTransaction(token, walletA, "an_sang", 300_000);
        createExpenseTransaction(token, walletB, "an_sang", 700_000);

        // Không lọc: thấy cả hai ví
        mockMvc.perform(get("/reports/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period_summary.total_expense").value(1_000_000));

        // Lọc ví A: chỉ thấy 300.000
        mockMvc.perform(get("/reports/home")
                        .header("Authorization", "Bearer " + token)
                        .param("wallet_id", walletA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period_summary.total_expense").value(300_000));

        mockMvc.perform(get("/reports/by-category-group")
                        .header("Authorization", "Bearer " + token)
                        .param("period", "month")
                        .param("wallet_id", walletB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(700_000));

        mockMvc.perform(get("/reports/by-category")
                        .header("Authorization", "Bearer " + token)
                        .param("period", "month")
                        .param("wallet_id", walletA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(300_000));

        mockMvc.perform(get("/reports/daily-trend")
                        .header("Authorization", "Bearer " + token)
                        .param("wallet_id", walletA))
                .andExpect(status().isOk());
    }

    @Test
    void reports_locTheoViCuaNguoiKhacTraVeRong() throws Exception {
        String ownerToken = registerAndGetAccessToken("chu.vi.bao.cao@example.com");
        String ownerWallet = createWallet(ownerToken, "Ví riêng", 5_000_000);
        createExpenseTransaction(ownerToken, ownerWallet, "an_sang", 500_000);

        String otherToken = registerAndGetAccessToken("nguoi.la.bao.cao@example.com");

        mockMvc.perform(get("/reports/home")
                        .header("Authorization", "Bearer " + otherToken)
                        .param("wallet_id", ownerWallet))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period_summary.total_expense").value(0));
    }

    @Test
    void childrenDetail_traVeSoGiaoDichThat_khongPhaiKhong() throws Exception {
        String token = registerAndGetAccessToken("chi.tiet.con.dem@example.com");
        String walletId = createWallet(token, "Ví đếm con", 5_000_000);

        createExpenseTransaction(token, walletId, "ca_phe", 30_000);
        createExpenseTransaction(token, walletId, "ca_phe", 20_000);
        createExpenseTransaction(token, walletId, "ca_phe", 18_000);
        createExpenseTransaction(token, walletId, "an_sang", 100_000);
        createExpenseTransaction(token, walletId, "an_sang", 42_000);

        mockMvc.perform(get("/reports/by-category")
                        .param("period", "month")
                        .param("level", "parent")
                        .param("type", "expense")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // Con "Cà phê": 68.000 từ 3 giao dịch
                .andExpect(jsonPath("$.data.items[0].children_detail[?(@.name == 'Cà phê')].transaction_count")
                        .value(3))
                // Con "Ăn sáng": 142.000 từ 2 giao dịch
                .andExpect(jsonPath("$.data.items[0].children_detail[?(@.name == 'Ăn sáng')].transaction_count")
                        .value(2));
    }
}
