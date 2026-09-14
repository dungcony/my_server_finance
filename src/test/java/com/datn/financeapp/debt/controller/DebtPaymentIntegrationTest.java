package com.datn.financeapp.debt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Test tích hợp trả nợ và huỷ trả nợ (DEBT-03, DEBT-04, D-46, D-47) — trọng tâm là RANH GIỚI
 * TRIGGER, mục ưu tiên số 2 của D-58.
 *
 * <p>Ca quan trọng nhất là {@link #cancelPayment_reopensSettledToOutstanding()}: trigger
 * {@code trg_debt_payments_sync} phải tự MỞ LẠI trạng thái từ settled về outstanding khi huỷ một
 * lần trả. Đây là bước dễ quên nhất của nghiệp vụ sổ nợ, và cũng là bằng chứng backend KHÔNG tự
 * ghi hai cột paid_amount/status — nếu backend ghi đè thì con số sẽ "đúng một cách tình cờ" và
 * test này không phát hiện được gì, nên nó luôn đọc thẳng từ CSDL để đối chiếu.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
class DebtPaymentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1kZWJ0LXBheW1lbnQtdGVzdC0zMmI");
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

    // Tạo khoản cho vay 3.000.000 và trả về id của nó.
    private String createLendingDebt(String token, String walletId, long amount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "lending");
        body.put("counterparty_name", "Anh Hùng");
        body.put("principal_amount", amount);
        body.put("wallet_id", walletId);

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

    private Map<?, ?> addPayment(String token, String debtId, long amount) throws Exception {
        String response = mockMvc.perform(post("/debts/" + debtId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", amount))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
    }

    private long walletBalance(String walletId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?", Long.class, UUID.fromString(walletId));
    }

    private long paidAmountFromDatabase(String debtId) {
        return jdbcTemplate.queryForObject(
                "SELECT paid_amount FROM debts WHERE id = ?", Long.class, UUID.fromString(debtId));
    }

    private String statusFromDatabase(String debtId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM debts WHERE id = ?", String.class, UUID.fromString(debtId));
    }

    // ---------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------

    /**
     * DEBT-03 — hai lần trả 1.000.000 cho khoản nợ 3.000.000: trigger cộng dồn paid_amount thành
     * 2.000.000, trạng thái vẫn outstanding vì chưa trả đủ.
     */
    @Test
    void addTwoPayments_accumulatesPaidAmount_keepsOutstanding() throws Exception {
        String token = registerAndGetAccessToken("tra.no.hai.lan@example.com");
        String walletId = createWallet(token, "Ví Trả Nợ", 10_000_000);
        String debtId = createLendingDebt(token, walletId, 3_000_000);

        addPayment(token, debtId, 1_000_000);
        Map<?, ?> secondPayment = addPayment(token, debtId, 1_000_000);

        // Response của chính lần trả thứ hai đã phải phản ánh giá trị SAU trigger.
        Map<?, ?> debtProgress = (Map<?, ?>) secondPayment.get("debt");
        assertThat(((Number) debtProgress.get("paid_amount")).longValue()).isEqualTo(2_000_000L);
        assertThat(((Number) debtProgress.get("remaining_amount")).longValue()).isEqualTo(1_000_000L);
        assertThat(debtProgress.get("status")).isEqualTo("outstanding");

        // GET /debts/{id} cũng phải khớp.
        mockMvc.perform(get("/debts/" + debtId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paid_amount").value(2_000_000))
                .andExpect(jsonPath("$.data.status").value("outstanding"))
                .andExpect(jsonPath("$.data.payment_history.length()").value(2));

        // Đối chiếu thẳng CSDL — trigger là nguồn sự thật của hai cột này.
        assertThat(paidAmountFromDatabase(debtId)).isEqualTo(2_000_000L);
        assertThat(statusFromDatabase(debtId)).isEqualTo("outstanding");

        // Ví: 10tr - 3tr (cho vay) + 1tr + 1tr (được trả) = 9tr.
        assertThat(walletBalance(walletId)).isEqualTo(9_000_000L);
    }

    // D-47 — trả vượt số còn lại bị chặn 400 với thông điệp chứa số tiền còn nợ chính xác.
    @Test
    void addPayment_exceedsRemaining_returns400() throws Exception {
        String token = registerAndGetAccessToken("tra.vuot@example.com");
        String walletId = createWallet(token, "Ví Trả Vượt", 10_000_000);
        String debtId = createLendingDebt(token, walletId, 3_000_000);

        addPayment(token, debtId, 1_000_000);
        long balanceAfterFirstPayment = walletBalance(walletId);

        // Còn nợ 2.000.000, ghi trả 2.500.000 -> chặn.
        mockMvc.perform(post("/debts/" + debtId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 2_500_000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EXCEEDS_REMAINING_AMOUNT"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("2.000.000")));

        // Không có tác dụng phụ nào: ví không đổi, paid_amount không đổi.
        assertThat(walletBalance(walletId)).isEqualTo(balanceAfterFirstPayment);
        assertThat(paidAmountFromDatabase(debtId)).isEqualTo(1_000_000L);
    }

    /**
     * D-46 + D-58 mục 2 — CA QUAN TRỌNG NHẤT của module: trả đủ để khoản nợ thành settled, rồi huỷ
     * MỘT lần trả. Trigger phải tự MỞ LẠI settled -&gt; outstanding, paid_amount giảm đúng, và số dư
     * ví hoàn tác đúng phần đã huỷ.
     */
    @Test
    void cancelPayment_reopensSettledToOutstanding() throws Exception {
        String token = registerAndGetAccessToken("huy.tra.no@example.com");
        String walletId = createWallet(token, "Ví Huỷ Trả Nợ", 10_000_000);
        String debtId = createLendingDebt(token, walletId, 3_000_000);

        // Trả đủ 3.000.000 bằng ba lần 1.000.000 -> settled.
        addPayment(token, debtId, 1_000_000);
        addPayment(token, debtId, 1_000_000);
        Map<?, ?> lastPayment = addPayment(token, debtId, 1_000_000);

        assertThat(statusFromDatabase(debtId)).isEqualTo("settled");
        assertThat(paidAmountFromDatabase(debtId)).isEqualTo(3_000_000L);
        assertThat(walletBalance(walletId)).isEqualTo(10_000_000L);

        String paymentId = (String) ((Map<?, ?>) lastPayment.get("payment")).get("id");
        String cancelledTransactionId =
                (String) ((Map<?, ?>) lastPayment.get("transaction")).get("id");

        mockMvc.perform(delete("/debts/" + debtId + "/payments/" + paymentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Trigger tự MỞ LẠI trạng thái — bước dễ quên nhất, chốt bằng đọc thẳng CSDL.
        assertThat(statusFromDatabase(debtId)).isEqualTo("outstanding");
        assertThat(paidAmountFromDatabase(debtId)).isEqualTo(2_000_000L);

        // Số dư ví hoàn tác đúng phần đã huỷ: 10tr - 1tr = 9tr.
        assertThat(walletBalance(walletId)).isEqualTo(9_000_000L);

        // Giao dịch tương ứng xoá MỀM, bản ghi trả nợ biến mất.
        Boolean isDeleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM transactions WHERE id = ?",
                Boolean.class,
                UUID.fromString(cancelledTransactionId));
        assertThat(isDeleted).isTrue();
        Integer paymentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM debt_payments WHERE id = ?", Integer.class, UUID.fromString(paymentId));
        assertThat(paymentCount).isZero();

        // Khoản nợ mở lại rồi thì ghi trả tiếp được bình thường.
        addPayment(token, debtId, 1_000_000);
        assertThat(statusFromDatabase(debtId)).isEqualTo("settled");
    }

    // DEBT-03 — khoản nợ đã trả xong không ghi thêm được (409).
    @Test
    void addPaymentToSettledDebt_returns409() throws Exception {
        String token = registerAndGetAccessToken("tra.khi.da.xong@example.com");
        String walletId = createWallet(token, "Ví Đã Trả Xong", 10_000_000);
        String debtId = createLendingDebt(token, walletId, 1_000_000);

        addPayment(token, debtId, 1_000_000);
        assertThat(statusFromDatabase(debtId)).isEqualTo("settled");

        mockMvc.perform(post("/debts/" + debtId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 100_000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEBT_ALREADY_SETTLED"));
    }

    // DEBT-03 — khoản nợ đã xoá nợ không ghi trả được (409).
    @Test
    void addPaymentToWrittenOffDebt_returns409() throws Exception {
        String token = registerAndGetAccessToken("tra.khi.da.xoa.no@example.com");
        String walletId = createWallet(token, "Ví Đã Xoá Nợ", 10_000_000);
        String debtId = createLendingDebt(token, walletId, 1_000_000);

        mockMvc.perform(post("/debts/" + debtId + "/write-off").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/debts/" + debtId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 100_000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEBT_WRITTEN_OFF"));
    }

    /**
     * DEBT-03 chiều ngược lại — khoản đi vay khi MÌNH trả thì sinh giao dịch CHI danh mục "Trả nợ",
     * ví giảm.
     */
    @Test
    void addPaymentToBorrowingDebt_createsExpenseTransaction_reducesBalance() throws Exception {
        String token = registerAndGetAccessToken("tra.no.di.vay@example.com");
        String walletId = createWallet(token, "Ví Trả Nợ Đi Vay", 1_000_000);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "borrowing");
        body.put("counterparty_name", "Chị Lan");
        body.put("principal_amount", 2_000_000);
        body.put("wallet_id", walletId);

        String response = mockMvc.perform(post("/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        String debtId = (String) ((Map<?, ?>) data.get("debt")).get("id");

        // Đi vay 2tr -> ví 3tr.
        assertThat(walletBalance(walletId)).isEqualTo(3_000_000L);

        Map<?, ?> payment = addPayment(token, debtId, 500_000);
        Map<?, ?> transaction = (Map<?, ?>) payment.get("transaction");
        assertThat(transaction.get("type")).isEqualTo("expense");

        String categoryName = jdbcTemplate.queryForObject(
                "SELECT c.name FROM transactions t JOIN categories c ON c.id = t.category_id WHERE t.id = ?",
                String.class,
                UUID.fromString((String) transaction.get("id")));
        assertThat(categoryName).isEqualTo("Trả nợ");

        // Trả 500k -> ví 2,5tr.
        assertThat(walletBalance(walletId)).isEqualTo(2_500_000L);
    }
}
