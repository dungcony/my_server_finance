package com.datn.financeapp.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Test tích hợp cho D-32/TXN-06 — xoá giao dịch gắn với một lần trả nợ phải bị chặn (409
 * TRANSACTION_LINKED_TO_DEBT), không tự sửa {@code debts.paid_amount} (cột do trigger
 * {@code trg_debt_payments_sync} sở hữu — api/04-GIAO-DICH.md mục 9).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
class TransactionDeleteDebtLinkIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci10eG4tZGVsZXRlLXRlc3QtMzJi");
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
        // debt_payments tham chiếu transactions (ON DELETE RESTRICT) và debts (ON DELETE CASCADE)
        // — xoá theo đúng thứ tự FK: debt_payments -> debts -> transactions -> wallets -> users.
        jdbcTemplate.update("DELETE FROM debt_payments");
        jdbcTemplate.update("DELETE FROM debts");
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private UUID currentUserId(String token) throws Exception {
        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/me")
                                .header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return UUID.fromString((String) data.get("id"));
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
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("id");
    }

    private UUID systemCategoryId(String type) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = ? LIMIT 1", UUID.class, type);
    }

    private String createTransaction(String token, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        Map<?, ?> transaction = (Map<?, ?>) data.get("transaction");
        return (String) transaction.get("id");
    }

    @Test
    void deleteTransactionLinkedToDebtPayment_returns409() throws Exception {
        String token = registerAndGetAccessToken("xoa.tra.no@example.com");
        UUID userId = currentUserId(token);
        String walletId = createWallet(token, "Ví Trả Nợ", 1_000_000);
        UUID categoryId = systemCategoryId("income");

        // Giao dịch thu — mô phỏng một lần thu tiền trả nợ.
        Map<String, Object> txnBody = new HashMap<>();
        txnBody.put("type", "income");
        txnBody.put("amount", 500000);
        txnBody.put("wallet_id", walletId);
        txnBody.put("category_id", categoryId.toString());
        String transactionId = createTransaction(token, txnBody);

        // Insert thủ công 1 khoản nợ + 1 lần trả nợ trỏ tới giao dịch vừa tạo — bảng debts/
        // debt_payments đã tồn tại từ V4 dù Phase 4 chưa code nghiệp vụ (đúng khuôn insertFakeTransaction
        // của WalletCrudIntegrationTest, dùng JdbcTemplate trực tiếp trong test).
        UUID debtId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO debts (id, user_id, wallet_id, type, counterparty_name, principal_amount, "
                        + "due_date, origin_transaction_id) "
                        + "VALUES (?, ?, ?, 'lending', 'Bạn A', 500000, CURRENT_DATE, ?)",
                debtId, userId, UUID.fromString(walletId), UUID.fromString(transactionId));
        jdbcTemplate.update(
                "INSERT INTO debt_payments (id, debt_id, transaction_id, amount) VALUES (gen_random_uuid(), ?, ?, 500000)",
                debtId, UUID.fromString(transactionId));

        mockMvc.perform(delete("/transactions/" + transactionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TRANSACTION_LINKED_TO_DEBT"));

        Boolean isDeleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM transactions WHERE id = ?", Boolean.class, UUID.fromString(transactionId));
        assertThat(isDeleted).isFalse();
    }
}
