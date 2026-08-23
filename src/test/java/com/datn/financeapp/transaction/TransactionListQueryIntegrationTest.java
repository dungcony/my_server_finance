package com.datn.financeapp.transaction;

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
import java.time.LocalDate;
import java.time.ZoneId;
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
 * TXN-01, TXN-02 — danh sách giao dịch có lọc/phân trang và bản gom theo ngày.
 *
 * <p>Ngày trong test luôn tính theo {@code Asia/Ho_Chi_Minh} chứ không theo múi giờ máy chạy CI,
 * khớp đúng cách {@code TransactionService} tính {@code day_label} — nếu để lệch, test sẽ chập chờn
 * mỗi khi CI chạy ở múi giờ khác.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionListQueryIntegrationTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1saXN0LXF1ZXJ5LXRlc3QtMDMwMw==");
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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
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
                "full_name", "Người Kiểm Thử");
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

    private String firstWalletId(String token) throws Exception {
        String response = mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        var wallets = (java.util.List<?>) parsed.get("data");
        return (String) ((Map<?, ?>) wallets.get(0)).get("id");
    }

    private String systemCategoryId(String type) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = ? LIMIT 1", String.class, type);
    }

    private String createTransaction(
            String token, String type, String walletId, String categoryId, long amount, LocalDate date, String note)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("amount", amount);
        body.put("wallet_id", walletId);
        body.put("date", date.toString());
        if (categoryId != null) {
            body.put("category_id", categoryId);
        }
        if (note != null) {
            body.put("note", note);
        }
        String response = mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        Map<?, ?> transaction = (Map<?, ?>) data.get("transaction");
        return (String) transaction.get("id");
    }

    @Test
    void listByDate_groupsTransactionsByDateWithCorrectLabels() throws Exception {
        String token = registerAndGetAccessToken("gom.theo.ngay@example.com");
        String walletId = firstWalletId(token);
        String expenseCategoryId = systemCategoryId("expense");
        String incomeCategoryId = systemCategoryId("income");

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDate yesterday = today.minusDays(1);

        // Hôm nay: thu 500.000 và chi 200.000 → day_total = +300.000
        createTransaction(token, "income", walletId, incomeCategoryId, 500_000, today, "Lương hôm nay");
        createTransaction(token, "expense", walletId, expenseCategoryId, 200_000, today, "Ăn trưa");
        // Hôm qua: chỉ có chi 150.000 → day_total âm
        createTransaction(token, "expense", walletId, expenseCategoryId, 150_000, yesterday, "Cà phê hôm qua");

        mockMvc.perform(get("/transactions/by-date").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].day_label").value("Hôm nay"))
                .andExpect(jsonPath("$.data.days[0].day_total").value(300_000))
                .andExpect(jsonPath("$.data.days[0].transaction.length()").value(2))
                .andExpect(jsonPath("$.data.days[1].day_label").value("Hôm qua"))
                .andExpect(jsonPath("$.data.days[1].day_total").value(-150_000))
                .andExpect(jsonPath("$.data.period_summary.total_income").value(500_000))
                .andExpect(jsonPath("$.data.period_summary.total_expense").value(350_000));
    }

    @Test
    void listByDate_excludesTransferFromDayTotal() throws Exception {
        String token = registerAndGetAccessToken("gom.ngay.loai.transfer@example.com");
        String walletA = firstWalletId(token);
        String expenseCategoryId = systemCategoryId("expense");
        LocalDate today = LocalDate.now(VIETNAM_ZONE);

        Map<String, Object> walletBody = Map.of("name", "Ví đích", "type", "cash", "initial_balance", 0);
        String walletResponse = mockMvc.perform(post("/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(walletBody)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String walletB = (String)
                ((Map<?, ?>) objectMapper.readValue(walletResponse, Map.class).get("data")).get("id");

        createTransaction(token, "expense", walletA, expenseCategoryId, 100_000, today, "Chi hôm nay");

        // Chuyển tiền số lớn cùng ngày — nếu lọt vào day_total sẽ sai lệch rõ rệt.
        Map<String, Object> transferBody = new HashMap<>();
        transferBody.put("type", "transfer");
        transferBody.put("amount", 3_000_000);
        transferBody.put("wallet_id", walletA);
        transferBody.put("destination_wallet_id", walletB);
        transferBody.put("date", today.toString());
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferBody)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/transactions/by-date")
                        .header("Authorization", "Bearer " + token)
                        .param("include_transfers", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].transaction.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].day_total").value(-100_000))
                .andExpect(jsonPath("$.data.period_summary.total_expense").value(100_000));
    }

    @Test
    void list_paginatesAndFiltersByTypeAndAmountRange() throws Exception {
        String token = registerAndGetAccessToken("loc.va.phan.trang@example.com");
        String walletId = firstWalletId(token);
        String expenseCategoryId = systemCategoryId("expense");
        String incomeCategoryId = systemCategoryId("income");
        LocalDate today = LocalDate.now(VIETNAM_ZONE);

        createTransaction(token, "expense", walletId, expenseCategoryId, 50_000, today, "Chi nhỏ");
        createTransaction(token, "expense", walletId, expenseCategoryId, 250_000, today, "Chi vừa");
        createTransaction(token, "expense", walletId, expenseCategoryId, 900_000, today, "Chi lớn");
        createTransaction(token, "income", walletId, incomeCategoryId, 700_000, today, "Thu nhập");

        // Lọc theo type=expense: 3 bản ghi, chia 2 trang mỗi trang 2 bản ghi.
        mockMvc.perform(get("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "expense")
                        .param("page", "1")
                        .param("page_size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.total_items").value(3))
                .andExpect(jsonPath("$.pagination.total_pages").value(2));

        // Lọc theo khoảng tiền: chỉ khoản 250.000 nằm trong [100.000, 500.000].
        mockMvc.perform(get("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("min_amount", "100000")
                        .param("max_amount", "500000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].amount").value(250_000));
    }

    @Test
    void detail_returnsTransactionForOwnerAnd404ForOtherUser() throws Exception {
        String ownerToken = registerAndGetAccessToken("chu.giao.dich@example.com");
        String walletId = firstWalletId(ownerToken);
        String expenseCategoryId = systemCategoryId("expense");
        LocalDate today = LocalDate.now(VIETNAM_ZONE);

        String transactionId =
                createTransaction(ownerToken, "expense", walletId, expenseCategoryId, 123_000, today, "Bí mật");

        mockMvc.perform(get("/transactions/" + transactionId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(123_000))
                .andExpect(jsonPath("$.data.note").value("Bí mật"));

        // Người dùng khác không được biết bản ghi có tồn tại hay không → 404, không phải 403.
        String otherToken = registerAndGetAccessToken("nguoi.khac@example.com");
        mockMvc.perform(get("/transactions/" + transactionId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }
}
