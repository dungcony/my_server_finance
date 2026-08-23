package com.datn.financeapp.wallet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.transaction.service.TransactionWriteCommand;
import com.datn.financeapp.transaction.service.TransactionWriter;
import com.datn.financeapp.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
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
 * Test tích hợp cho D-36/D-37 (TXN-09): ví có giao dịch tương lai phải trả đúng
 * {@code current_balance} (đã trừ ngược) và {@code projected_balance} (chỉ khi có giao dịch
 * tương lai); ví không có giao dịch tương lai phải bỏ hẳn field {@code projected_balance} khỏi
 * JSON response.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletFutureBalanceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci13YWxsZXQtZnV0dXJlLXRlc3QtMzJi");
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
    private TransactionWriter transactionWriter;

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

    private UUID currentUserId(String token) throws Exception {
        String response = mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
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

    @Test
    void walletWithFutureTransaction_returnsBothCurrentAndProjectedBalance() throws Exception {
        String token = registerAndGetAccessToken("vi.tuong.lai@example.com");
        UUID userId = currentUserId(token);
        String walletId = createWallet(token, "Ví Kiểm Tra", 1_000_000);
        UUID walletUuid = UUID.fromString(walletId);

        // Giao dịch expense hôm nay 200000
        transactionWriter.write(new TransactionWriteCommand(
                null, userId, walletUuid, null, systemCategoryId("expense"), "expense", 200_000,
                LocalDate.now(), null, null, "manual", true, null, null, null));

        // Giao dịch income ngày mai 500000
        transactionWriter.write(new TransactionWriteCommand(
                null, userId, walletUuid, null, systemCategoryId("income"), "income", 500_000,
                LocalDate.now().plusDays(1), null, null, "manual", true, null, null, null));

        mockMvc.perform(get("/wallets/" + walletId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_balance").value(800_000))
                .andExpect(jsonPath("$.data.projected_balance").value(1_300_000));
    }

    @Test
    void walletWithoutFutureTransaction_omitsProjectedBalanceField() throws Exception {
        String token = registerAndGetAccessToken("vi.khong.tuong.lai@example.com");
        UUID userId = currentUserId(token);
        String walletId = createWallet(token, "Ví Kiểm Tra", 1_000_000);
        UUID walletUuid = UUID.fromString(walletId);

        // Chỉ có giao dịch hôm nay, không có giao dịch tương lai.
        transactionWriter.write(new TransactionWriteCommand(
                null, userId, walletUuid, null, systemCategoryId("expense"), "expense", 100_000,
                LocalDate.now(), null, null, "manual", true, null, null, null));

        mockMvc.perform(get("/wallets/" + walletId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_balance").value(900_000))
                .andExpect(jsonPath("$.data.projected_balance").doesNotExist());
    }
}
