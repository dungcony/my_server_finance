package com.datn.financeapp.wallet;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Test tích hợp qua HTTP thật cho POST /wallets/{id}/adjust-balance (WALLET-08) và
 * POST /wallets/{id}/reconcile (WALLET-07) — api/02-VI.md mục 9-10, Task 2 plan 02-04,
 * D-28 mục 2.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletAdjustBalanceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci13YWxsZXQtYWRqdXN0LWJhbGFuY2U=");
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

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
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
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("access_token");
    }

    private String createWallet(String token, String name, String type, long initialBalance) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", type,
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

    /** Ví mới tạo bằng initial_balance=450000 nghiễm nhiên current_balance=450000 (mục 4). */
    private String createWalletWithBalance450k(String token, String name) throws Exception {
        return createWallet(token, name, "bank", 450_000);
    }

    @Test
    void actualBalanceLessThanCurrent_createsExpenseAdjustmentTransaction() throws Exception {
        String token = registerAndGetAccessToken("kiem.ke.thieu@example.com");
        String walletId = createWalletWithBalance450k(token, "Ví Kiểm Kê 1");

        Map<String, Object> body = Map.of("actual_balance", 300_000);

        mockMvc.perform(post("/wallets/" + walletId + "/adjust-balance")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.difference").value(-150_000))
                .andExpect(jsonPath("$.data.adjusted").value(true))
                .andExpect(jsonPath("$.data.transaction_type").value("expense"));

        Long txnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE wallet_id = ? AND source = 'adjustment' "
                        + "AND type = 'expense' AND amount = 150000",
                Long.class, UUID.fromString(walletId));
        assertThat(txnCount).isEqualTo(1);

        Long balance = jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?", Long.class, UUID.fromString(walletId));
        assertThat(balance).isEqualTo(300_000);
    }

    @Test
    void actualBalanceGreaterThanCurrent_createsIncomeAdjustmentTransaction() throws Exception {
        String token = registerAndGetAccessToken("kiem.ke.du@example.com");
        String walletId = createWalletWithBalance450k(token, "Ví Kiểm Kê 2");

        Map<String, Object> body = Map.of("actual_balance", 500_000);

        mockMvc.perform(post("/wallets/" + walletId + "/adjust-balance")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.difference").value(50_000))
                .andExpect(jsonPath("$.data.transaction_type").value("income"));

        Long balance = jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?", Long.class, UUID.fromString(walletId));
        assertThat(balance).isEqualTo(500_000);
    }

    @Test
    void actualBalanceEqualsCurrent_createsNoTransaction() throws Exception {
        String token = registerAndGetAccessToken("kiem.ke.dung@example.com");
        String walletId = createWalletWithBalance450k(token, "Ví Kiểm Kê 3");

        Map<String, Object> body = Map.of("actual_balance", 450_000);

        mockMvc.perform(post("/wallets/" + walletId + "/adjust-balance")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adjusted").value(false))
                .andExpect(jsonPath("$.data.transaction_id").doesNotExist());

        Long txnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE wallet_id = ?", Long.class, UUID.fromString(walletId));
        assertThat(txnCount).isEqualTo(0);
    }

    @Test
    void negativeActualBalance_returns400InvalidAmount() throws Exception {
        String token = registerAndGetAccessToken("kiem.ke.am@example.com");
        String walletId = createWalletWithBalance450k(token, "Ví Kiểm Kê 4");

        Map<String, Object> body = Map.of("actual_balance", -1000);

        mockMvc.perform(post("/wallets/" + walletId + "/adjust-balance")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_AMOUNT"));
    }

    @Test
    void afterAdjustment_reconcileStillMatches() throws Exception {
        String token = registerAndGetAccessToken("kiem.ke.doi.chieu@example.com");
        String walletId = createWalletWithBalance450k(token, "Ví Kiểm Kê 5");

        Map<String, Object> adjustBody = Map.of("actual_balance", 300_000);
        mockMvc.perform(post("/wallets/" + walletId + "/adjust-balance")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjustBody)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/wallets/" + walletId + "/reconcile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.difference").value(0))
                .andExpect(jsonPath("$.data.matches").value(true));
    }

    @Test
    void adjustmentTransaction_respectsCountsInReportFlag() throws Exception {
        String token = registerAndGetAccessToken("kiem.ke.counts@example.com");
        String walletId = createWalletWithBalance450k(token, "Ví Kiểm Kê 6");

        Map<String, Object> body = Map.of("actual_balance", 400_000, "counts_in_report", false);

        mockMvc.perform(post("/wallets/" + walletId + "/adjust-balance")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts_in_report").value(false));

        Long countsFalseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE wallet_id = ? AND source = 'adjustment' "
                        + "AND counts_in_report = false",
                Long.class, UUID.fromString(walletId));
        assertThat(countsFalseCount).isEqualTo(1);

        // Không truyền counts_in_report -> mặc định true
        String walletId2 = createWalletWithBalance450k(token, "Ví Kiểm Kê 6b");
        Map<String, Object> body2 = Map.of("actual_balance", 400_000);
        mockMvc.perform(post("/wallets/" + walletId2 + "/adjust-balance")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts_in_report").value(true));
    }

    @Test
    void reconcile_detectsDrift_doesNotSilentlyFixWhenAutoFixFalse() throws Exception {
        String token = registerAndGetAccessToken("doi.chieu.lech@example.com");
        String walletId = createWalletWithBalance450k(token, "Ví Đối Chiếu Lệch");

        // Mô phỏng bug hệ thống: sửa thẳng current_balance qua JDBC, KHÔNG qua API.
        jdbcTemplate.update(
                "UPDATE wallets SET current_balance = 999999 WHERE id = ?", UUID.fromString(walletId));

        mockMvc.perform(post("/wallets/" + walletId + "/reconcile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matches").value(false))
                .andExpect(jsonPath("$.data.stored_balance").value(999999))
                .andExpect(jsonPath("$.data.computed_balance").value(450_000))
                .andExpect(jsonPath("$.data.was_fixed").value(false));

        Long balanceAfter = jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?", Long.class, UUID.fromString(walletId));
        assertThat(balanceAfter).isEqualTo(999999L);
    }
}
