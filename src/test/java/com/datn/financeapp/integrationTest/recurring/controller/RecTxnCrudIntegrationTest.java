package com.datn.financeapp.integrationTest.recurring.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.time.LocalDate;
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
 * Test tích hợp phần REST API CRUD và quản lý lịch con của khoản định kỳ (RECUR-01, RECUR-02).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class RecTxnCrudIntegrationTest {

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
    private OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM recurring_transaction_schedules");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM recurring_transactions");
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
                "type", "CASH",
                "balance", initialBalance,
                "icon_code", "vi_tien",
                "color", "#2f8272");
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

    private String createRecurring(
            String token, String walletId, String categoryCode, LocalDate startDate, String frequency) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("display_name", "Tiền thuê nhà");
        body.put("type", "EXPENSE");
        body.put("amount", 4_500_000);
        body.put("wallet_id", walletId);
        body.put("category_code", categoryCode);
        body.put("frequency", frequency);
        body.put("interval", 1);
        body.put("start_date", startDate.toString());

        mockMvc.perform(post("/recurring")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        return jdbcTemplate.queryForObject(
                "SELECT id FROM recurring_transactions WHERE name = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                "Tiền thuê nhà");
    }

    private Map<String, Object> recurringRowFromDatabase(String recurringId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM recurring_transactions WHERE id = ?", UUID.fromString(recurringId));
    }

    private int transactionCountForCategory(String categoryCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE category_code = ? AND NOT is_deleted",
                Integer.class,
                categoryCode);
    }

    private long walletBalance(String walletId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM wallets WHERE id = ?", Long.class, UUID.fromString(walletId));
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    /**
     * RECUR-01: Tạo khoản định kỳ chỉ sinh cấu hình và các buổi lịch đệm (PENDING),
     * TUYỆT ĐỐI không sinh giao dịch ngay (phải chờ tới ngày), ví không bị trừ tiền.
     */
    @Test
    void createRecurring_setsStartDate_andGeneratesInitialSchedules_noImmediateTransaction() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.tao@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);
        LocalDate startDate = LocalDate.now().plusDays(10);

        String recurringId = createRecurring(token, walletId, "tien_thue_nha", startDate, "MONTH");

        Map<String, Object> row = recurringRowFromDatabase(recurringId);
        assertThat(((java.sql.Date) row.get("start_date")).toLocalDate()).isEqualTo(startDate);
        assertThat(row.get("is_enabled")).isEqualTo(true);
        assertThat(row.get("is_deleted")).isEqualTo(false);

        // Khẳng định cốt lõi: 12 buổi đệm được sinh nhưng chưa buổi nào chạy, chưa có giao dịch trừ ví
        Integer scheduleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recurring_transaction_schedules WHERE recurring_id = ?",
                Integer.class,
                UUID.fromString(recurringId));
        assertThat(scheduleCount).isEqualTo(12);

        Map<String, Object> firstSchedule = jdbcTemplate.queryForMap(
                "SELECT * FROM recurring_transaction_schedules WHERE recurring_id = ? ORDER BY run_date ASC LIMIT 1",
                UUID.fromString(recurringId));
        assertThat(((java.sql.Date) firstSchedule.get("run_date")).toLocalDate()).isEqualTo(startDate);
        assertThat(firstSchedule.get("status")).isEqualTo("PENDING");

        assertThat(transactionCountForCategory("tien_thue_nha")).isZero();
        assertThat(walletBalance(walletId)).isEqualTo(20_000_000L);
    }

    /**
     * Tạm dừng (disable) và kích hoạt lại (enable) khoản định kỳ.
     */
    @Test
    void enableAndDisableRecurring_updatesIsEnabledStatus() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.enable.disable@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);
        String recurringId = createRecurring(token, walletId, "tien_thue_nha", LocalDate.now().plusDays(10), "MONTH");

        // Disable
        mockMvc.perform(post("/recurring/" + recurringId + "/disable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Map<String, Object> rowDisabled = recurringRowFromDatabase(recurringId);
        assertThat(rowDisabled.get("is_enabled")).isEqualTo(false);

        // Enable
        mockMvc.perform(post("/recurring/" + recurringId + "/enable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Map<String, Object> rowEnabled = recurringRowFromDatabase(recurringId);
        assertThat(rowEnabled.get("is_enabled")).isEqualTo(true);
    }

    /**
     * Bỏ qua (skip) và khôi phục (unskip) một buổi lịch cụ thể.
     */
    @Test
    void skipAndUnskipSchedule_updatesScheduleStatus() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.skip@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);
        String recurringId = createRecurring(token, walletId, "tien_thue_nha", LocalDate.now().plusDays(10), "MONTH");

        // Lấy danh sách lịch con
        String schedulesResponse = mockMvc.perform(get("/recurring/" + recurringId + "/schedules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> parsed = objectMapper.readValue(schedulesResponse, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        List<?> items = (List<?>) data.get("items");
        assertThat(items).isNotEmpty();
        Map<?, ?> firstItem = (Map<?, ?>) items.get(0);
        String scheduleId = (String) firstItem.get("id");

        // Skip buổi đầu tiên
        mockMvc.perform(post("/recurring/schedules/" + scheduleId + "/skip")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String statusInDb = jdbcTemplate.queryForObject(
                "SELECT status FROM recurring_transaction_schedules WHERE id = ?",
                String.class,
                UUID.fromString(scheduleId));
        assertThat(statusInDb).isEqualTo("SKIPPED");

        // Skip lần 2 khi không còn PENDING phải báo lỗi 400
        mockMvc.perform(post("/recurring/schedules/" + scheduleId + "/skip")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Unskip khôi phục lại PENDING
        mockMvc.perform(post("/recurring/schedules/" + scheduleId + "/unskip")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        statusInDb = jdbcTemplate.queryForObject(
                "SELECT status FROM recurring_transaction_schedules WHERE id = ?",
                String.class,
                UUID.fromString(scheduleId));
        assertThat(statusInDb).isEqualTo("PENDING");
    }

    /**
     * Khoản định kỳ của người khác phải trả 404 chứ không phải 403: không để lộ việc bản ghi có tồn tại hay không.
     */
    @Test
    void getRecurringOfAnotherUser_returnsNotFound() throws Exception {
        String ownerToken = registerAndGetAccessToken("dinh.ky.chu@example.com");
        String walletId = createWallet(ownerToken, "Vietcombank", 20_000_000);
        String recurringId = createRecurring(ownerToken, walletId, "tien_thue_nha", LocalDate.now().plusDays(10), "MONTH");

        String intruderToken = registerAndGetAccessToken("dinh.ky.ke.la@example.com");

        mockMvc.perform(get("/recurring/" + recurringId).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    /**
     * Danh mục không tồn tại hoặc không phải danh mục con (leaf) phải bị chặn từ lúc tạo lịch.
     */
    @Test
    void createRecurring_categoryNotFoundOrNotLeaf_returnsNotFound() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.sai.cate@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);

        // "an_uong" là danh mục gốc (is_leaf = false)
        Map<String, Object> body = new HashMap<>();
        body.put("display_name", "Tiền thuê nhà");
        body.put("type", "EXPENSE");
        body.put("amount", 4_500_000);
        body.put("wallet_id", walletId);
        body.put("category_code", "an_uong");
        body.put("frequency", "MONTH");
        body.put("interval", 1);
        body.put("start_date", LocalDate.now().plusDays(3).toString());

        mockMvc.perform(post("/recurring")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    /**
     * Xoá khoản định kỳ (soft delete) và tự động dọn dẹp các buổi lịch chưa chạy (deleteUnprocessed).
     */
    @Test
    void deleteRecurring_softDeletesRecurring_andDeletesUnprocessedSchedules() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.xoa@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);
        String recurringId = createRecurring(token, walletId, "tien_thue_nha", LocalDate.now().plusDays(10), "MONTH");

        Integer initialSchedules = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recurring_transaction_schedules WHERE recurring_id = ?",
                Integer.class,
                UUID.fromString(recurringId));
        assertThat(initialSchedules).isEqualTo(12);

        mockMvc.perform(delete("/recurring/" + recurringId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Recurring bị đánh dấu xoá mềm
        Map<String, Object> row = recurringRowFromDatabase(recurringId);
        assertThat(row.get("is_deleted")).isEqualTo(true);

        // Các buổi PENDING chưa chạy bị dọn dẹp
        Integer remainingSchedules = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recurring_transaction_schedules WHERE recurring_id = ? AND status = 'PENDING'",
                Integer.class,
                UUID.fromString(recurringId));
        assertThat(remainingSchedules).isZero();
    }
}
