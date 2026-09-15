package com.datn.financeapp.recurring.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
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
 * Test tích hợp phần request-response của khoản định kỳ (RECUR-01, RECUR-02).
 *
 * <p>Hai khẳng định cốt lõi của api/09 mục A2/A3 mà rất dễ cài sai: tạo khoản định kỳ KHÔNG được
 * sinh giao dịch ngay (phải chờ tới ngày), và {@code run-now} KHÔNG được đụng vào
 * {@code next_run_date} (lịch tự động vẫn chạy như cũ).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
class RecurringCrudIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1yZWN1cnJpbmctY3J1ZC0zMmJ5dGU");
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
    private com.datn.financeapp.auth.repository.OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        // OTP nằm ở Redis, không bị Testcontainers PostgreSQL dọn hộ.
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM recurring_transactions");
        jdbcTemplate.update("DELETE FROM categories WHERE user_id IS NOT NULL");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private String createWallet(String token, String name, long initialBalance) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "cash",
                "initial_balance", initialBalance);
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

    private String createRootCategory(String token, String name, String type) throws Exception {
        String iconId = jdbcTemplate.queryForObject("SELECT id FROM icons WHERE code = ?", String.class, "khac");
        String groupId =
                jdbcTemplate.queryForObject("SELECT id FROM category_groups WHERE name = ?", String.class, "Khác");
        Map<String, Object> body = Map.of(
                "name", name,
                "type", type,
                "icon_id", iconId,
                "color", "#3d6b7d",
                "category_group_id", groupId);
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

    private String createRecurring(
            String token, String walletId, String categoryId, LocalDate startDate, String frequency) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("display_name", "Tiền thuê nhà");
        body.put("type", "expense");
        body.put("amount", 4_500_000);
        body.put("wallet_id", walletId);
        body.put("category_id", categoryId);
        body.put("frequency", frequency);
        body.put("interval", 1);
        body.put("start_date", startDate.toString());

        String response = mockMvc.perform(post("/recurring")
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

    private Map<String, Object> recurringRowFromDatabase(String recurringId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM recurring_transactions WHERE id = ?", UUID.fromString(recurringId));
    }

    private LocalDate dateColumn(Map<String, Object> row, String column) {
        return ((java.sql.Date) row.get(column)).toLocalDate();
    }

    private int transactionCountForRecurring(String recurringId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE recurring_id = ? AND NOT is_deleted",
                Integer.class,
                UUID.fromString(recurringId));
    }

    private long walletBalance(String walletId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?", Long.class, UUID.fromString(walletId));
    }

    // ---------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------

    /**
     * RECUR-01, api/09 mục A2 "Việc máy chủ phải làm" #2-4 — tạo khoản định kỳ chỉ đặt lịch, TUYỆT
     * ĐỐI không sinh giao dịch ngay. Sinh ngay sẽ trừ oan ví một kỳ mà người dùng chưa hề tiêu.
     */
    @Test
    void createRecurring_setsNextRunDateToStartDate_noImmediateTransaction() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.tao@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);
        String categoryId = createRootCategory(token, "Tiền thuê nhà test", "expense");
        LocalDate startDate = LocalDate.now().plusDays(10);

        String recurringId = createRecurring(token, walletId, categoryId, startDate, "month");

        Map<String, Object> row = recurringRowFromDatabase(recurringId);
        assertThat(dateColumn(row, "next_run_date")).isEqualTo(startDate);
        assertThat(row.get("is_enabled")).isEqualTo(true);
        assertThat(row.get("last_run_date")).isNull();

        // Khẳng định cốt lõi: chưa tới ngày thì chưa có đồng nào được ghi.
        assertThat(transactionCountForRecurring(recurringId)).isZero();
        assertThat(walletBalance(walletId)).isEqualTo(20_000_000L);
    }

    /**
     * RECUR-02, api/09 mục A3 — {@code run-now} là hành động CHỦ ĐỘNG của người dùng (trả tiền nhà
     * sớm) nên vẫn ghi được kể cả khi khoản đang tạm dừng, nhưng KHÔNG được dời
     * {@code next_run_date}: lần chạy tự động vẫn diễn ra đúng lịch cũ.
     */
    @Test
    void runNow_doesNotChangeNextRunDate() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.chay.ngay@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);
        String categoryId = createRootCategory(token, "Tiền thuê nhà test", "expense");
        LocalDate startDate = LocalDate.now().plusDays(10);
        String recurringId = createRecurring(token, walletId, categoryId, startDate, "month");

        // Tạm dừng trước để chứng minh run-now không phụ thuộc is_enabled.
        mockMvc.perform(post("/recurring/" + recurringId + "/pause")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("is_enabled", false))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/recurring/" + recurringId + "/run-now").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transaction.type").value("expense"));

        // Giao dịch được ghi ngay hôm nay, ví bị trừ.
        assertThat(transactionCountForRecurring(recurringId)).isEqualTo(1);
        assertThat(jdbcTemplate
                        .queryForObject(
                                "SELECT date FROM transactions WHERE recurring_id = ?",
                                java.sql.Date.class,
                                UUID.fromString(recurringId))
                        .toLocalDate())
                .isEqualTo(LocalDate.now());
        assertThat(walletBalance(walletId)).isEqualTo(20_000_000L - 4_500_000L);

        // Nhưng LỊCH không đổi — đây là khẳng định cốt lõi của A3.
        Map<String, Object> row = recurringRowFromDatabase(recurringId);
        assertThat(dateColumn(row, "next_run_date")).isEqualTo(startDate);
        assertThat(row.get("last_run_date")).isNull();
        assertThat(row.get("is_enabled")).isEqualTo(false);
    }

    /**
     * Gọi {@code run-now} hai lần trong cùng một ngày bị {@code uq_txn_recurring_date} chặn, và
     * phải biến thành lỗi nghiệp vụ 409 rõ ràng chứ không phải 500 lộ chi tiết CSDL.
     */
    @Test
    void runNow_calledTwiceSameDay_returnsAlreadyRunToday() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.trung@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);
        String categoryId = createRootCategory(token, "Tiền thuê nhà test", "expense");
        String recurringId = createRecurring(token, walletId, categoryId, LocalDate.now().plusDays(10), "month");

        mockMvc.perform(post("/recurring/" + recurringId + "/run-now").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/recurring/" + recurringId + "/run-now").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_RUN_TODAY"));

        assertThat(transactionCountForRecurring(recurringId)).isEqualTo(1);
    }

    /**
     * T-04-13 — khoản định kỳ của người khác phải trả 404 chứ không phải 403: không để lộ việc bản
     * ghi có tồn tại hay không. Điều kiện quyền nằm ngay trong SQL của repository.
     */
    @Test
    void getRecurringOfAnotherUser_returnsNotFound() throws Exception {
        String ownerToken = registerAndGetAccessToken("dinh.ky.chu@example.com");
        String walletId = createWallet(ownerToken, "Vietcombank", 20_000_000);
        String categoryId = createRootCategory(ownerToken, "Tiền thuê nhà test", "expense");
        String recurringId = createRecurring(ownerToken, walletId, categoryId, LocalDate.now().plusDays(10), "month");

        String intruderToken = registerAndGetAccessToken("dinh.ky.ke.la@example.com");

        mockMvc.perform(get("/recurring/" + recurringId).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    /**
     * api/09 mục A2 — danh mục thu gán cho khoản chi phải bị chặn ngay từ lúc tạo lịch, không đợi
     * tới lúc tác vụ nền chạy mới phát hiện (khi đó lỗi sẽ lặp lại mỗi ngày trong log).
     */
    @Test
    void createRecurring_categoryTypeMismatch_returnsBadRequest() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.lech.loai@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);
        String incomeCategoryId = createRootCategory(token, "Lương test", "income");

        Map<String, Object> body = new HashMap<>();
        body.put("display_name", "Tiền thuê nhà");
        body.put("type", "expense");
        body.put("amount", 4_500_000);
        body.put("wallet_id", walletId);
        body.put("category_id", incomeCategoryId);
        body.put("frequency", "month");
        body.put("start_date", LocalDate.now().plusDays(3).toString());

        mockMvc.perform(post("/recurring")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_TYPE_MISMATCH"));
    }

    /**
     * {@code recurring_transactions} KHÔNG có cột {@code is_deleted} (schema V2) nên xoá là xoá
     * CỨNG. Nhưng {@code fk_txn_recurring ON DELETE SET NULL} bảo toàn các giao dịch đã sinh: tiền
     * đã tiêu thật thì phải nằm nguyên trong sổ, chỉ mất đường liên kết ngược về lịch.
     */
    @Test
    void deleteRecurring_keepsAlreadyGeneratedTransactions() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.xoa@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);
        String categoryId = createRootCategory(token, "Tiền thuê nhà test", "expense");
        String recurringId = createRecurring(token, walletId, categoryId, LocalDate.now().plusDays(10), "month");

        mockMvc.perform(post("/recurring/" + recurringId + "/run-now").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/recurring/" + recurringId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Lịch biến mất hẳn...
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM recurring_transactions WHERE id = ?",
                        Integer.class,
                        UUID.fromString(recurringId)))
                .isZero();
        // ...nhưng giao dịch đã ghi vẫn còn, chỉ mất recurring_id (ON DELETE SET NULL).
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM transactions WHERE NOT is_deleted", Integer.class))
                .isEqualTo(1);
        assertThat(walletBalance(walletId)).isEqualTo(20_000_000L - 4_500_000L);
    }
}
