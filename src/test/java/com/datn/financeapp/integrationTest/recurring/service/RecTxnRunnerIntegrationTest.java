package com.datn.financeapp.integrationTest.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.transaction.service.RecTxnScheduleService;
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
 * Test tích hợp tác vụ xử lý các buổi lịch đến hạn của khoản định kỳ (RECUR-03).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class RecTxnRunnerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1yZWN1cnJpbmctcnVubmVyLTMyYnk");
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
    private RecTxnScheduleService recTxnScheduleService;

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
            String token,
            String walletId,
            String categoryCode,
            String displayName,
            long amount,
            LocalDate startDate,
            String frequency) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("display_name", displayName);
        body.put("type", "EXPENSE");
        body.put("amount", amount);
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
                displayName);
    }

    private long walletBalance(String walletId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM wallets WHERE id = ?", Long.class, UUID.fromString(walletId));
    }

    // ---------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------

    /**
     * Tác vụ quét lịch đến hạn: các buổi lịch có run_date <= hôm nay sẽ được xử lý thành SUCCESS,
     * tự động sinh giao dịch thực tế vào bảng transactions và duy trì bộ đệm tối đa 12 buổi PENDING.
     */
    @Test
    void runDueRecurring_dueSchedules_generatesTransactionsAndMaintainsBuffer() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.runner.due@example.com");
        String walletId = createWallet(token, "Vietcombank", 50_000_000);
        String recurringId = createRecurring(
                token, walletId, "tien_thue_nha", "Tiền thuê nhà", 4_500_000, LocalDate.now().plusDays(30), "MONTH");

        // Lấy 3 buổi đầu tiên và chỉnh ngày chạy về quá khứ để đến hạn chạy
        List<UUID> scheduleIds = jdbcTemplate.queryForList(
                "SELECT id FROM recurring_transaction_schedules WHERE recurring_id = ? ORDER BY run_date ASC LIMIT 3",
                UUID.class,
                UUID.fromString(recurringId));
        assertThat(scheduleIds).hasSize(3);

        LocalDate today = LocalDate.now();
        jdbcTemplate.update("UPDATE recurring_transaction_schedules SET run_date = ? WHERE id = ?",
                java.sql.Date.valueOf(today.minusMonths(2)), scheduleIds.get(0));
        jdbcTemplate.update("UPDATE recurring_transaction_schedules SET run_date = ? WHERE id = ?",
                java.sql.Date.valueOf(today.minusMonths(1)), scheduleIds.get(1));
        jdbcTemplate.update("UPDATE recurring_transaction_schedules SET run_date = ? WHERE id = ?",
                java.sql.Date.valueOf(today), scheduleIds.get(2));

        // Chạy xử lý lịch định kỳ
        recTxnScheduleService.processDueSchedules();

        // 3 buổi đã chạy thành công
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM recurring_transaction_schedules WHERE id IN (?, ?, ?) ORDER BY run_date ASC",
                String.class,
                scheduleIds.get(0), scheduleIds.get(1), scheduleIds.get(2));
        assertThat(statuses).containsExactly("SUCCESS", "SUCCESS", "SUCCESS");

        // 3 giao dịch thực tế đã được sinh
        Integer txnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE category_code = 'tien_thue_nha' AND NOT is_deleted",
                Integer.class);
        assertThat(txnCount).isEqualTo(3);

        // Số dư ví được trừ đúng 3 kỳ
        assertThat(walletBalance(walletId)).isEqualTo(50_000_000L - 3 * 4_500_000L);

        // Bộ đệm PENDING được duy trì gối đầu
        Integer pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recurring_transaction_schedules WHERE recurring_id = ? AND status = 'PENDING'",
                Integer.class,
                UUID.fromString(recurringId));
        assertThat(pendingCount).isEqualTo(12);
    }

    /**
     * Lỗi ở một buổi lịch (ví dụ vi phạm ràng buộc dữ liệu) không làm chết cả lô,
     * buổi hỏng chuyển sang FAILED, còn buổi lành vẫn SUCCESS.
     */
    @Test
    void runDueRecurring_oneRecurringFails_othersStillSucceed() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.runner.err@example.com");
        String healthyWalletId = createWallet(token, "Ví Lành", 30_000_000);
        String brokenWalletId = createWallet(token, "Ví Hỏng", 30_000_000);

        String healthyId = createRecurring(
                token, healthyWalletId, "tien_thue_nha", "Khoản Lành", 4_500_000, LocalDate.now().plusDays(30), "MONTH");
        String brokenId = createRecurring(
                token, brokenWalletId, "tien_thue_nha", "Khoản Hỏng", 1_000_000, LocalDate.now().plusDays(30), "MONTH");

        // Chỉnh 1 buổi của mỗi khoản về hôm nay
        UUID healthyScheduleId = jdbcTemplate.queryForObject(
                "SELECT id FROM recurring_transaction_schedules WHERE recurring_id = ? ORDER BY run_date ASC LIMIT 1",
                UUID.class, UUID.fromString(healthyId));
        UUID brokenScheduleId = jdbcTemplate.queryForObject(
                "SELECT id FROM recurring_transaction_schedules WHERE recurring_id = ? ORDER BY run_date ASC LIMIT 1",
                UUID.class, UUID.fromString(brokenId));

        LocalDate today = LocalDate.now();
        jdbcTemplate.update("UPDATE recurring_transaction_schedules SET run_date = ? WHERE id = ?",
                java.sql.Date.valueOf(today), healthyScheduleId);
        jdbcTemplate.update("UPDATE recurring_transaction_schedules SET run_date = ? WHERE id = ?",
                java.sql.Date.valueOf(today), brokenScheduleId);

        // Ép khoản hỏng trỏ tới ví không tồn tại (gây Foreign Key exception khi insert transaction)
        jdbcTemplate.update("UPDATE recurring_transactions SET wallet_id = ? WHERE id = ?",
                UUID.randomUUID(), UUID.fromString(brokenId));

        assertThatCode(() -> recTxnScheduleService.processDueSchedules()).doesNotThrowAnyException();

        // Buổi lành chạy thành công
        String healthyStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM recurring_transaction_schedules WHERE id = ?",
                String.class, healthyScheduleId);
        assertThat(healthyStatus).isEqualTo("SUCCESS");
        assertThat(walletBalance(healthyWalletId)).isEqualTo(30_000_000L - 4_500_000L);

        // Buổi hỏng bị đánh dấu FAILED
        String brokenStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM recurring_transaction_schedules WHERE id = ?",
                String.class, brokenScheduleId);
        assertThat(brokenStatus).isEqualTo("FAILED");
    }

    /**
     * Khoản định kỳ bị tắt (is_enabled = false): các buổi đến hạn sẽ chuyển sang SKIPPED
     * thay vì tạo giao dịch, tránh lặp lại quét mỗi giờ.
     */
    @Test
    void runDueRecurring_disabledRecurring_marksDueSchedulesSkipped() throws Exception {
        String token = registerAndGetAccessToken("dinh.ky.runner.disabled@example.com");
        String walletId = createWallet(token, "Vietcombank", 20_000_000);
        String recurringId = createRecurring(
                token, walletId, "tien_thue_nha", "Tiền thuê nhà", 4_500_000, LocalDate.now().plusDays(30), "MONTH");

        UUID scheduleId = jdbcTemplate.queryForObject(
                "SELECT id FROM recurring_transaction_schedules WHERE recurring_id = ? ORDER BY run_date ASC LIMIT 1",
                UUID.class, UUID.fromString(recurringId));

        LocalDate today = LocalDate.now();
        jdbcTemplate.update("UPDATE recurring_transaction_schedules SET run_date = ? WHERE id = ?",
                java.sql.Date.valueOf(today), scheduleId);

        // Tắt khoản định kỳ
        mockMvc.perform(post("/recurring/" + recurringId + "/disable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        recTxnScheduleService.processDueSchedules();

        // Buổi đến hạn được chuyển thành SKIPPED
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM recurring_transaction_schedules WHERE id = ?",
                String.class, scheduleId);
        assertThat(status).isEqualTo("SKIPPED");

        // Không có giao dịch nào được sinh ra và ví giữ nguyên
        Integer txnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE category_code = 'tien_thue_nha' AND NOT is_deleted",
                Integer.class);
        assertThat(txnCount).isZero();
        assertThat(walletBalance(walletId)).isEqualTo(20_000_000L);
    }
}
