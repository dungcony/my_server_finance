package com.datn.financeapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * JOB-04 (D-57, api/08-SO-NO.md mục 9) — nhắc nợ đúng ba mốc: còn 7 ngày, còn 1 ngày, quá hạn nhắc
 * lại mỗi 7 ngày. Gọi trực tiếp {@code debtReminderJob.run()} qua bean Spring.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DebtReminderJobIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1kZWJ0LXJlbWluZGVyLWpvYi10ZXN0");
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
    private DebtReminderJob debtReminderJob;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM debt_payments");
        jdbcTemplate.update("DELETE FROM debts");
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "full_name", "Người Kiểm Thử Nhắc Nợ");
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

    private String createLendingDebt(String token, String walletId, long amount, String dueDate) throws Exception {
        return createLendingDebt(token, walletId, amount, dueDate, null);
    }

    /**
     * {@code issuedDate} tách thành tham số riêng vì DebtService chặn {@code due_date} trước
     * {@code issued_date} (mặc định hôm nay) — muốn dựng khoản nợ ĐÃ QUÁ HẠN thì phải lùi cả ngày
     * phát sinh, đúng như dữ liệu thật của một khoản vay cũ.
     */
    private String createLendingDebt(
            String token, String walletId, long amount, String dueDate, String issuedDate)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "lending");
        body.put("counterparty_name", "Chị Lan");
        body.put("principal_amount", amount);
        body.put("wallet_id", walletId);
        body.put("due_date", dueDate);
        if (issuedDate != null) {
            body.put("issued_date", issuedDate);
        }

        String response = mockMvc.perform(post("/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) ((Map<?, ?>) data.get("debt")).get("id");
    }

    private int countReminders(String debtId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE reference_id = ? AND type = 'debt_reminder'",
                Integer.class,
                UUID.fromString(debtId));
        return count == null ? 0 : count;
    }

    // ---------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------

    /** due_date = hôm nay + 7 ngày -> đúng 1 bản ghi nhắc nợ type=debt_reminder. */
    @Test
    void sendDueReminders_sevenDaysBeforeDue_createsNotification() throws Exception {
        String token = registerAndGetAccessToken("nhac.no.bay.ngay@example.com");
        String walletId = createWallet(token, "Ví Nhắc Nợ 7 Ngày", 10_000_000);
        String debtId = createLendingDebt(
                token, walletId, 2_000_000, java.time.LocalDate.now().plusDays(7).toString());

        debtReminderJob.run();

        assertThat(countReminders(debtId))
                .as("due_date còn đúng 7 ngày phải sinh 1 thông báo debt_reminder")
                .isEqualTo(1);
    }

    /** due_date = hôm nay + 3 ngày -> KHÔNG rơi vào mốc nào trong ba mốc, không có bản ghi nào. */
    @Test
    void sendDueReminders_threeDaysBeforeDue_createsNoNotification() throws Exception {
        String token = registerAndGetAccessToken("nhac.no.ba.ngay@example.com");
        String walletId = createWallet(token, "Ví Nhắc Nợ 3 Ngày", 10_000_000);
        String debtId = createLendingDebt(
                token, walletId, 2_000_000, java.time.LocalDate.now().plusDays(3).toString());

        debtReminderJob.run();

        assertThat(countReminders(debtId))
                .as("due_date còn 3 ngày không rơi vào mốc 7/1/quá hạn nào -> không có thông báo")
                .isEqualTo(0);
    }

    /**
     * due_date = ĐÚNG HÔM NAY -> phải nhắc. Điều kiện cũ {@code daysUntilDue < 0} bỏ sót ca này,
     * khiến người vỡ hạn đúng hôm nay im lặng tới tận 7 ngày sau mới được nhắc — đúng lúc cần
     * nhắc nhất thì không có gì.
     */
    @Test
    void sendDueReminders_dueToday_createsNotification() throws Exception {
        String token = registerAndGetAccessToken("nhac.no.hom.nay@example.com");
        String walletId = createWallet(token, "Ví Nhắc Nợ Hôm Nay", 10_000_000);
        String debtId =
                createLendingDebt(token, walletId, 2_000_000, java.time.LocalDate.now().toString());

        debtReminderJob.run();

        assertThat(countReminders(debtId))
                .as("đến hạn đúng hôm nay là mốc quá hạn ĐẦU TIÊN, phải có thông báo")
                .isEqualTo(1);
    }

    /** Quá hạn đúng 7 ngày -> rơi vào chu kỳ nhắc lại mỗi 7 ngày. */
    @Test
    void sendDueReminders_sevenDaysOverdue_createsNotification() throws Exception {
        String token = registerAndGetAccessToken("nhac.no.qua.han.bay@example.com");
        String walletId = createWallet(token, "Ví Nhắc Nợ Quá Hạn", 10_000_000);
        String debtId = createLendingDebt(
                token,
                walletId,
                2_000_000,
                java.time.LocalDate.now().minusDays(7).toString(),
                java.time.LocalDate.now().minusDays(30).toString());

        debtReminderJob.run();

        assertThat(countReminders(debtId))
                .as("quá hạn 7 ngày rơi đúng chu kỳ nhắc lại")
                .isEqualTo(1);
    }

    /**
     * Job chạy HAI LẦN trong cùng một ngày (deploy lại, retry sau lỗi, hoặc hai instance) chỉ được
     * sinh MỘT thông báo. Chống trùng nằm ở {@code uq_notif_budget_alert} (V9 — UNIQUE đầy đủ trên
     * mọi type, không riêng budget_alert) qua {@code ON CONFLICT DO NOTHING}.
     */
    @Test
    void sendDueReminders_runTwiceSameDay_createsOnlyOneNotification() throws Exception {
        String token = registerAndGetAccessToken("nhac.no.chay.hai.lan@example.com");
        String walletId = createWallet(token, "Ví Nhắc Nợ Chạy Lại", 10_000_000);
        String debtId = createLendingDebt(
                token, walletId, 2_000_000, java.time.LocalDate.now().plusDays(7).toString());

        debtReminderJob.run();
        debtReminderJob.run();

        assertThat(countReminders(debtId))
                .as("chạy lại trong cùng ngày không được sinh thông báo trùng")
                .isEqualTo(1);
    }
}
