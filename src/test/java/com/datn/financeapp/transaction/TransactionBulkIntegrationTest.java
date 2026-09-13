package com.datn.financeapp.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * TXN-04 — {@code POST /transactions/bulk}. Ba quyết định thiết kế được kiểm chứng ở đây:
 *
 * <ul>
 *   <li><b>D-34</b> mỗi dòng một DB transaction riêng: dòng lỗi giữa lô KHÔNG cuốn theo dòng đã
 *       ghi trước đó và cũng không chặn dòng sau.
 *   <li><b>D-35a</b> dòng hỏng ở GIỮA lô không cuốn theo dòng đã ghi trước đó, cũng không chặn
 *       dòng sau — bằng chứng trực tiếp rằng mỗi dòng chạy trong một DB transaction riêng. Mô
 *       phỏng bằng {@code wallet_id} không tồn tại. Lưu ý: dòng này bị {@code requireWalletAccess}
 *       chặn ở tầng service (404) TRƯỚC khi chạm CSDL, nên đây vẫn là lỗi dữ liệu chứ không phải
 *       lỗi hạ tầng thật (mất kết nối CSDL giữa chừng không mô phỏng được qua Testcontainers mà
 *       không phá container). Tính chất "không cuốn theo dòng khác" thì giống hệt nhau.
 *   <li><b>D-35</b> một {@code Idempotency-Key} bảo vệ CẢ LÔ: gửi lại cùng key trả nguyên kết quả
 *       lần đầu, không ghi thêm bản ghi nào.
 * </ul>
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionBulkIntegrationTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1idWxrLXRyYW5zYWN0aW9uLXRlc3Q=");
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
        jdbcTemplate.update("DELETE FROM idempotency_keys");
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
                "username", "Người Kiểm Thử");
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
        List<?> wallets = (List<?>) parsed.get("data");
        return (String) ((Map<?, ?>) wallets.get(0)).get("id");
    }

    private String systemCategoryId(String type) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = ? LIMIT 1", String.class, type);
    }

    private long walletBalance(String walletId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?::uuid", Long.class, walletId);
    }

    private long transactionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Long.class);
    }

    private Map<String, Object> expenseRow(String walletId, String categoryId, long amount, String note) {
        Map<String, Object> row = new HashMap<>();
        row.put("type", "expense");
        row.put("amount", amount);
        row.put("wallet_id", walletId);
        row.put("category_id", categoryId);
        row.put("date", LocalDate.now(VIETNAM_ZONE).toString());
        row.put("note", note);
        return row;
    }

    /**
     * D-34 — dòng giữa lô sai dữ liệu (danh mục thu gán cho khoản chi) chỉ vào {@code row_errors};
     * hai dòng hợp lệ vẫn commit và số dư ví trừ đúng bằng tổng hai dòng đó.
     */
    @Test
    void bulkCreate_withOneInvalidRow_savesValidRowsAndReportsError() throws Exception {
        String token = registerAndGetAccessToken("bulk.dong.loi.du.lieu@example.com");
        String walletId = firstWalletId(token);
        String expenseCategoryId = systemCategoryId("expense");
        String incomeCategoryId = systemCategoryId("income");
        long balanceBefore = walletBalance(walletId);

        List<Map<String, Object>> items = List.of(
                expenseRow(walletId, expenseCategoryId, 45_000, "Dòng hợp lệ 1"),
                // Danh mục type=income gán cho giao dịch type=expense -> CATEGORY_TYPE_MISMATCH
                expenseRow(walletId, incomeCategoryId, 999_000, "Dòng sai danh mục"),
                expenseRow(walletId, expenseCategoryId, 60_000, "Dòng hợp lệ 2"));

        mockMvc.perform(post("/transactions/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.success_count").value(2))
                .andExpect(jsonPath("$.data.failure_count").value(1))
                .andExpect(jsonPath("$.data.row_errors.length()").value(1))
                .andExpect(jsonPath("$.data.row_errors[0].row_index").value(1))
                .andExpect(jsonPath("$.data.row_errors[0].code").value("CATEGORY_TYPE_MISMATCH"));

        assertThat(transactionCount()).isEqualTo(2);
        // Chỉ hai dòng hợp lệ tác động số dư — dòng lỗi không trừ 999.000 nào.
        assertThat(walletBalance(walletId)).isEqualTo(balanceBefore - 45_000 - 60_000);
    }

    /**
     * D-35a — dòng hỏng ở GIỮA lô (ví không tồn tại) không kéo theo dòng đã ghi trước đó, cũng
     * không chặn dòng sau. Đây là bằng chứng trực tiếp rằng mỗi dòng chạy trong một DB
     * transaction riêng: nếu cả lô nằm chung một transaction, dòng 0 đã bị cuốn theo khi dòng 1
     * hỏng.
     */
    @Test
    void bulkCreate_withSystemErrorRow_keepsRowsBeforeAndAfterIntact() throws Exception {
        String token = registerAndGetAccessToken("bulk.loi.he.thong@example.com");
        String walletId = firstWalletId(token);
        String expenseCategoryId = systemCategoryId("expense");
        long balanceBefore = walletBalance(walletId);

        // Ví không tồn tại trong CSDL -> requireWalletAccess trả NOT_FOUND trước khi chạm DB.
        String ghostWalletId = UUID.randomUUID().toString();

        List<Map<String, Object>> items = List.of(
                expenseRow(walletId, expenseCategoryId, 30_000, "Trước dòng hỏng"),
                expenseRow(ghostWalletId, expenseCategoryId, 70_000, "Dòng hỏng"),
                expenseRow(walletId, expenseCategoryId, 20_000, "Sau dòng hỏng"));

        mockMvc.perform(post("/transactions/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.success_count").value(2))
                .andExpect(jsonPath("$.data.failure_count").value(1))
                .andExpect(jsonPath("$.data.row_errors[0].row_index").value(1));

        // Dòng 0 (trước) và dòng 2 (sau) đều còn nguyên trong CSDL.
        assertThat(transactionCount()).isEqualTo(2);
        assertThat(walletBalance(walletId)).isEqualTo(balanceBefore - 30_000 - 20_000);
    }

    // T-03-11 — vượt 50 dòng bị chặn NGAY ĐẦU, không dòng nào được xử lý.
    @Test
    void bulkCreate_exceeding50Rows_returns400TooManyRows() throws Exception {
        String token = registerAndGetAccessToken("bulk.qua.nhieu.dong@example.com");
        String walletId = firstWalletId(token);
        String expenseCategoryId = systemCategoryId("expense");

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            items.add(expenseRow(walletId, expenseCategoryId, 1_000, "Dòng " + i));
        }

        mockMvc.perform(post("/transactions/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_ROWS"));

        assertThat(transactionCount()).isZero();
    }

    // D-35 — một Idempotency-Key bảo vệ cả lô; gửi lại không ghi thêm bản ghi nào.
    @Test
    void bulkCreate_replayWithSameIdempotencyKey_doesNotDuplicateRows() throws Exception {
        String token = registerAndGetAccessToken("bulk.idempotency@example.com");
        String walletId = firstWalletId(token);
        String expenseCategoryId = systemCategoryId("expense");
        String idempotencyKey = UUID.randomUUID().toString();

        String payload = objectMapper.writeValueAsString(Map.of(
                "items",
                List.of(
                        expenseRow(walletId, expenseCategoryId, 25_000, "Lần đầu 1"),
                        expenseRow(walletId, expenseCategoryId, 35_000, "Lần đầu 2"))));

        mockMvc.perform(post("/transactions/bulk")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.success_count").value(2));

        long countAfterFirstCall = transactionCount();
        long balanceAfterFirstCall = walletBalance(walletId);

        mockMvc.perform(post("/transactions/bulk")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(jsonPath("$.data.success_count").value(2));

        assertThat(transactionCount()).isEqualTo(countAfterFirstCall);
        assertThat(walletBalance(walletId)).isEqualTo(balanceAfterFirstCall);
    }

    // T-03-12 — không có đường tắt bỏ qua kiểm quyền ví chỉ vì đang ở trong lô.
    @Test
    void bulkCreate_withWalletOfAnotherUser_rejectsThatRowOnly() throws Exception {
        String victimToken = registerAndGetAccessToken("bulk.nan.nhan@example.com");
        String victimWalletId = firstWalletId(victimToken);
        long victimBalanceBefore = walletBalance(victimWalletId);

        String attackerToken = registerAndGetAccessToken("bulk.ke.tan.cong@example.com");
        String attackerWalletId = firstWalletId(attackerToken);
        String expenseCategoryId = systemCategoryId("expense");

        List<Map<String, Object>> items = List.of(
                expenseRow(attackerWalletId, expenseCategoryId, 10_000, "Ví của chính mình"),
                expenseRow(victimWalletId, expenseCategoryId, 500_000, "Ví của người khác"));

        mockMvc.perform(post("/transactions/bulk")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.success_count").value(1))
                .andExpect(jsonPath("$.data.failure_count").value(1))
                .andExpect(jsonPath("$.data.row_errors[0].row_index").value(1))
                .andExpect(jsonPath("$.data.row_errors[0].code").value("NOT_FOUND"));

        // Ví nạn nhân không hề bị chạm tới.
        assertThat(walletBalance(victimWalletId)).isEqualTo(victimBalanceBefore);
    }
}
