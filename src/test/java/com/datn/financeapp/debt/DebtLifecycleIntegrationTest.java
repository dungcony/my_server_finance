package com.datn.financeapp.debt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Test tích hợp vòng đời khoản nợ (DEBT-01, DEBT-05, DEBT-06) — tạo khoản nợ sinh giao dịch thật,
 * write-off không sinh giao dịch, xoá khoản nợ hoàn tác TOÀN BỘ giao dịch liên quan.
 *
 * <p>Điểm chốt xuyên suốt: số dư ví sau mỗi thao tác phải khớp chính xác, vì mọi đường ghi đều đi
 * qua {@code TransactionWriter}/{@code TransactionService} chứ không tự cộng trừ.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DebtLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1kZWJ0LWxpZmVjeWNsZS10ZXN0LTMy");
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
        // Thứ tự theo FK: debt_payments -> debts -> transactions -> wallets -> users.
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

    private Map<?, ?> createDebt(String token, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
    }

    private Map<?, ?> addPayment(String token, String debtId, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/debts/" + debtId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
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

    private Map<String, Object> lendingDebtBody(String walletId, long amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "lending");
        body.put("counterparty_name", "Anh Hùng");
        body.put("principal_amount", amount);
        body.put("wallet_id", walletId);
        body.put("issued_date", LocalDate.now().toString());
        return body;
    }

    // ---------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------

    /** DEBT-01 — cho vay sinh đúng MỘT giao dịch chi danh mục "Cho vay", ví giảm đúng số tiền gốc. */
    @Test
    void createLendingDebt_createsExpenseTransaction_reducesBalance() throws Exception {
        String token = registerAndGetAccessToken("tao.khoan.no@example.com");
        String walletId = createWallet(token, "Ví Cho Vay", 10_000_000);

        Map<?, ?> data = createDebt(token, lendingDebtBody(walletId, 5_000_000));

        Map<?, ?> originTransaction = (Map<?, ?>) data.get("origin_transaction");
        assertThat(originTransaction.get("type")).isEqualTo("expense");
        assertThat(((Number) originTransaction.get("amount")).longValue()).isEqualTo(5_000_000L);

        Map<?, ?> newBalance = (Map<?, ?>) data.get("new_balance");
        assertThat(((Number) newBalance.get("balance")).longValue()).isEqualTo(5_000_000L);
        assertThat(walletBalance(walletId)).isEqualTo(5_000_000L);

        // Đúng MỘT giao dịch được sinh ra, gắn đúng danh mục hệ thống "Cho vay".
        Integer transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE NOT is_deleted", Integer.class);
        assertThat(transactionCount).isEqualTo(1);

        String categoryName = jdbcTemplate.queryForObject(
                "SELECT c.name FROM transactions t JOIN categories c ON c.id = t.category_id "
                        + "WHERE t.id = ?",
                String.class,
                UUID.fromString((String) originTransaction.get("id")));
        assertThat(categoryName).isEqualTo("Cho vay");

        // Khoản nợ khởi tạo đúng: chưa trả đồng nào, còn nợ nguyên.
        Map<?, ?> debt = (Map<?, ?>) data.get("debt");
        assertThat(((Number) debt.get("paid_amount")).longValue()).isZero();
        assertThat(((Number) debt.get("remaining_amount")).longValue()).isEqualTo(5_000_000L);
        assertThat(debt.get("status")).isEqualTo("outstanding");
    }

    /** DEBT-01 — chiều ngược lại: đi vay sinh giao dịch THU, ví tăng. */
    @Test
    void createBorrowingDebt_createsIncomeTransaction_increasesBalance() throws Exception {
        String token = registerAndGetAccessToken("di.vay@example.com");
        String walletId = createWallet(token, "Ví Đi Vay", 1_000_000);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "borrowing");
        body.put("counterparty_name", "Chị Lan");
        body.put("principal_amount", 3_000_000);
        body.put("wallet_id", walletId);

        Map<?, ?> data = createDebt(token, body);

        Map<?, ?> originTransaction = (Map<?, ?>) data.get("origin_transaction");
        assertThat(originTransaction.get("type")).isEqualTo("income");
        assertThat(walletBalance(walletId)).isEqualTo(4_000_000L);

        String categoryName = jdbcTemplate.queryForObject(
                "SELECT c.name FROM transactions t JOIN categories c ON c.id = t.category_id WHERE t.id = ?",
                String.class,
                UUID.fromString((String) originTransaction.get("id")));
        assertThat(categoryName).isEqualTo("Đi vay");
    }

    /** DEBT-05 — write-off chỉ đổi trạng thái, KHÔNG sinh giao dịch mới, số dư ví không đổi. */
    @Test
    void writeOffDebt_changesStatusOnly_doesNotCreateTransaction() throws Exception {
        String token = registerAndGetAccessToken("xoa.no@example.com");
        String walletId = createWallet(token, "Ví Xoá Nợ", 10_000_000);

        Map<?, ?> data = createDebt(token, lendingDebtBody(walletId, 2_000_000));
        String debtId = (String) ((Map<?, ?>) data.get("debt")).get("id");

        long balanceBefore = walletBalance(walletId);
        Integer transactionCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE NOT is_deleted", Integer.class);

        mockMvc.perform(post("/debts/" + debtId + "/write-off")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Bạn gặp khó khăn, quyết định không đòi nữa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("written_off"));

        assertThat(walletBalance(walletId)).isEqualTo(balanceBefore);
        Integer transactionCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE NOT is_deleted", Integer.class);
        assertThat(transactionCountAfter).isEqualTo(transactionCountBefore);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM debts WHERE id = ?", String.class, UUID.fromString(debtId));
        assertThat(status).isEqualTo("written_off");
    }

    /** DEBT-05 — write-off lần hai bị chặn 409, không âm thầm thành công. */
    @Test
    void writeOffAlreadyWrittenOffDebt_returns409() throws Exception {
        String token = registerAndGetAccessToken("xoa.no.hai.lan@example.com");
        String walletId = createWallet(token, "Ví Xoá Nợ Hai Lần", 10_000_000);

        Map<?, ?> data = createDebt(token, lendingDebtBody(walletId, 1_000_000));
        String debtId = (String) ((Map<?, ?>) data.get("debt")).get("id");

        mockMvc.perform(post("/debts/" + debtId + "/write-off").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/debts/" + debtId + "/write-off").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEBT_WRITTEN_OFF"));
    }

    /**
     * DEBT-06 — xoá khoản nợ có 2 lần trả: cả giao dịch gốc VÀ 2 giao dịch trả nợ đều xoá mềm, số
     * dư ví hoàn tác đúng tổng ảnh hưởng của cả 3 giao dịch, bản ghi nợ biến mất hoàn toàn (xoá
     * cứng, D-48) và debt_payments cascade theo.
     */
    @Test
    void deleteDebtWithPayments_softDeletesAllTransactions_restoresBalance() throws Exception {
        String token = registerAndGetAccessToken("xoa.khoan.no@example.com");
        String walletId = createWallet(token, "Ví Xoá Khoản Nợ", 10_000_000);

        Map<?, ?> data = createDebt(token, lendingDebtBody(walletId, 3_000_000));
        String debtId = (String) ((Map<?, ?>) data.get("debt")).get("id");
        assertThat(walletBalance(walletId)).isEqualTo(7_000_000L);

        addPayment(token, debtId, Map.of("amount", 1_000_000));
        addPayment(token, debtId, Map.of("amount", 500_000));
        assertThat(walletBalance(walletId)).isEqualTo(8_500_000L);

        mockMvc.perform(delete("/debts/" + debtId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Số dư ví quay đúng về giá trị ban đầu.
        assertThat(walletBalance(walletId)).isEqualTo(10_000_000L);

        // Bản ghi nợ xoá CỨNG, debt_payments cascade theo.
        Integer debtCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM debts WHERE id = ?", Integer.class, UUID.fromString(debtId));
        assertThat(debtCount).isZero();
        Integer paymentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM debt_payments WHERE debt_id = ?", Integer.class, UUID.fromString(debtId));
        assertThat(paymentCount).isZero();

        // Cả 3 giao dịch xoá MỀM — giữ lại audit trail, không xoá cứng.
        Integer aliveTransactions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE NOT is_deleted", Integer.class);
        assertThat(aliveTransactions).isZero();
        Integer softDeletedTransactions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE is_deleted", Integer.class);
        assertThat(softDeletedTransactions).isEqualTo(3);
    }

    /** Riêng tư (T-04-09) — khoản nợ của người khác trả 404, không phải 403. */
    @Test
    void getDebtOfAnotherUser_returns404() throws Exception {
        String ownerToken = registerAndGetAccessToken("chu.so.huu@example.com");
        String ownerWalletId = createWallet(ownerToken, "Ví Riêng", 5_000_000);
        Map<?, ?> data = createDebt(ownerToken, lendingDebtBody(ownerWalletId, 1_000_000));
        String debtId = (String) ((Map<?, ?>) data.get("debt")).get("id");

        String otherToken = registerAndGetAccessToken("nguoi.la@example.com");

        mockMvc.perform(get("/debts/" + debtId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
