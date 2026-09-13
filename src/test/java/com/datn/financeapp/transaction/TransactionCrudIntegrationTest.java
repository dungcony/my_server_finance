package com.datn.financeapp.transaction;

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

// Test tích hợp cho POST /transactions (TXN-03, api/04-GIAO-DICH.md mục 4) — Task 1 plan 03-02.
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
class TransactionCrudIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci10eG4tY3J1ZC10ZXN0LTMyYg==");
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
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private String firstWalletId(String token) throws Exception {
        return authSupport.firstWalletId(token);
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

    @Test
    void createExpenseTransaction_deductsWalletBalance_returns201() throws Exception {
        String token = registerAndGetAccessToken("tao.chi@example.com");
        String walletId = createWallet(token, "Ví Chi Tiêu", 500_000);
        UUID categoryId = systemCategoryId("expense");

        Map<String, Object> body = new HashMap<>();
        body.put("type", "expense");
        body.put("amount", 68000);
        body.put("wallet_id", walletId);
        body.put("category_id", categoryId.toString());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transaction.type").value("expense"))
                .andExpect(jsonPath("$.data.new_balance.wallet_id").value(walletId));

        var wallet = walletRepository.findById(UUID.fromString(walletId)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(wallet.getCurrentBalance()).isEqualTo(500_000 - 68000);
    }

    @Test
    void createTransferTransaction_deductsSourceAddsDestination_returns201() throws Exception {
        String token = registerAndGetAccessToken("tao.chuyen@example.com");
        String walletA = createWallet(token, "Ví A", 500_000);
        String walletB = createWallet(token, "Ví B", 200_000);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "transfer");
        body.put("amount", 100000);
        body.put("wallet_id", walletA);
        body.put("destination_wallet_id", walletB);

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transaction.type").value("transfer"));

        var reloadedA = walletRepository.findById(UUID.fromString(walletA)).orElseThrow();
        var reloadedB = walletRepository.findById(UUID.fromString(walletB)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloadedA.getCurrentBalance()).isEqualTo(500_000 - 100000);
        org.assertj.core.api.Assertions.assertThat(reloadedB.getCurrentBalance()).isEqualTo(200_000 + 100000);
    }

    @Test
    void createExpenseWithoutCategory_returns400CategoryRequired() throws Exception {
        String token = registerAndGetAccessToken("thieu.danh.muc@example.com");
        String walletId = firstWalletId(token);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "expense");
        body.put("amount", 68000);
        body.put("wallet_id", walletId);

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_REQUIRED"));
    }

    @Test
    void createTransferWithCategory_returns400CategoryNotAllowed() throws Exception {
        String token = registerAndGetAccessToken("chuyen.co.danh.muc@example.com");
        String walletA = firstWalletId(token);
        String walletB = createWallet(token, "Ví B", 200_000);
        UUID categoryId = systemCategoryId("expense");

        Map<String, Object> body = new HashMap<>();
        body.put("type", "transfer");
        body.put("amount", 50000);
        body.put("wallet_id", walletA);
        body.put("destination_wallet_id", walletB);
        body.put("category_id", categoryId.toString());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_ALLOWED"));
    }

    @Test
    void createTransactionWithFutureDate_isAccepted_returns201() throws Exception {
        String token = registerAndGetAccessToken("ngay.tuong.lai@example.com");
        String walletId = firstWalletId(token);
        UUID categoryId = systemCategoryId("income");

        Map<String, Object> body = new HashMap<>();
        body.put("type", "income");
        body.put("amount", 500000);
        body.put("wallet_id", walletId);
        body.put("category_id", categoryId.toString());
        body.put("date", LocalDate.now().plusDays(30).toString());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transaction.date").value(LocalDate.now().plusDays(30).toString()));
    }

    @Test
    void createExpenseWithMismatchedCategoryType_returns400CategoryTypeMismatch() throws Exception {
        String token = registerAndGetAccessToken("sai.loai.danh.muc@example.com");
        String walletId = firstWalletId(token);
        UUID incomeCategoryId = systemCategoryId("income");

        Map<String, Object> body = new HashMap<>();
        body.put("type", "expense");
        body.put("amount", 68000);
        body.put("wallet_id", walletId);
        body.put("category_id", incomeCategoryId.toString());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_TYPE_MISMATCH"));
    }

    @Test
    void createExpense_withDestinationWalletId_returnsDestinationWalletNotAllowed() throws Exception {
        String token = registerAndGetAccessToken("chi.co.vi.dich@example.com");
        String walletA = firstWalletId(token);
        String walletB = createWallet(token, "Ví B", 200_000);
        UUID categoryId = systemCategoryId("expense");

        Map<String, Object> body = new HashMap<>();
        body.put("type", "expense");
        body.put("amount", 68000);
        body.put("wallet_id", walletA);
        body.put("destination_wallet_id", walletB);
        body.put("category_id", categoryId.toString());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DESTINATION_WALLET_NOT_ALLOWED"));
    }
}
