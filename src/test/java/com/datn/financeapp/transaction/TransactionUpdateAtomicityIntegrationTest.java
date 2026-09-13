package com.datn.financeapp.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Test tích hợp cho D-33/TXN-05 — trình tự 3 bước bất di bất dịch khi sửa giao dịch
 * (CLAUDE.md §4, api/04-GIAO-DICH.md mục 8). Đây là luồng rủi ro cao nhất của toàn dự án: PHẢI
 * hoàn tác đúng ảnh hưởng CŨ trước khi áp dụng ảnh hưởng MỚI, không bao giờ cộng/trừ chênh lệch.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
class TransactionUpdateAtomicityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci10eG4tdXBkYXRlLXRlc3QtMzJi");
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

    /**
     * Ca kinh điển bắt buộc phải chặn (api/04-GIAO-DICH.md mục 8): sửa khoản chi 100.000 ở ví A
     * thành khoản chi 80.000 ở ví B. Sai — trừ chênh lệch 20.000 vào ví mới — sẽ để ví A bị trừ
     * oan 100.000. Đúng — hoàn tác rồi áp lại — ví A được cộng trả đủ 100.000, ví B bị trừ đúng
     * 80.000.
     */
    @Test
    void updateChangesAmountAndWallet_bothOldAndNewWalletBalancesAreExactlyCorrect() throws Exception {
        String token = registerAndGetAccessToken("sua.doi.vi@example.com");
        String walletA = createWallet(token, "Ví A", 1_000_000);
        String walletB = createWallet(token, "Ví B", 500_000);
        UUID categoryId = systemCategoryId("expense");

        Map<String, Object> createBody = new HashMap<>();
        createBody.put("type", "expense");
        createBody.put("amount", 100000);
        createBody.put("wallet_id", walletA);
        createBody.put("category_id", categoryId.toString());
        String transactionId = createTransaction(token, createBody);

        // Sau khi tạo: ví A = 1.000.000 - 100.000 = 900.000
        assertThat(walletRepository.findById(UUID.fromString(walletA)).orElseThrow().getCurrentBalance())
                .isEqualTo(900_000);

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("type", "expense");
        updateBody.put("amount", 80000);
        updateBody.put("wallet_id", walletB);
        updateBody.put("category_id", categoryId.toString());

        mockMvc.perform(put("/transactions/" + transactionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk());

        var reloadedA = walletRepository.findById(UUID.fromString(walletA)).orElseThrow();
        var reloadedB = walletRepository.findById(UUID.fromString(walletB)).orElseThrow();

        // Đúng: ví A được cộng trả nguyên 100.000 -> quay lại 1.000.000.
        assertThat(reloadedA.getCurrentBalance()).isEqualTo(1_000_000);
        // Đúng: ví B bị trừ đúng 80.000 -> còn 420.000 (KHÔNG phải 480.000 nếu chỉ trừ chênh lệch).
        assertThat(reloadedB.getCurrentBalance()).isEqualTo(420_000);
    }

    /**
     * D-33: sửa giao dịch transfer chạm tối đa 4 ví — A->B đổi thành C->D. Cả 4 ví phải khoá
     * đúng thứ tự UUID.compareTo() và tính đúng tuyệt đối, không lẫn lộn ví nào.
     */
    @Test
    void updateTransferChangingBothWallets_allFourWalletsEndUpCorrect() throws Exception {
        String token = registerAndGetAccessToken("sua.chuyen.tien@example.com");
        String walletA = createWallet(token, "Ví A", 1_000_000);
        String walletB = createWallet(token, "Ví B", 500_000);
        String walletC = createWallet(token, "Ví C", 700_000);
        String walletD = createWallet(token, "Ví D", 300_000);

        Map<String, Object> createBody = new HashMap<>();
        createBody.put("type", "transfer");
        createBody.put("amount", 200000);
        createBody.put("wallet_id", walletA);
        createBody.put("destination_wallet_id", walletB);
        String transactionId = createTransaction(token, createBody);

        // Sau khi tạo: A = 1.000.000 - 200.000 = 800.000; B = 500.000 + 200.000 = 700.000
        assertThat(walletRepository.findById(UUID.fromString(walletA)).orElseThrow().getCurrentBalance())
                .isEqualTo(800_000);
        assertThat(walletRepository.findById(UUID.fromString(walletB)).orElseThrow().getCurrentBalance())
                .isEqualTo(700_000);

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("type", "transfer");
        updateBody.put("amount", 150000);
        updateBody.put("wallet_id", walletC);
        updateBody.put("destination_wallet_id", walletD);

        mockMvc.perform(put("/transactions/" + transactionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk());

        var reloadedA = walletRepository.findById(UUID.fromString(walletA)).orElseThrow();
        var reloadedB = walletRepository.findById(UUID.fromString(walletB)).orElseThrow();
        var reloadedC = walletRepository.findById(UUID.fromString(walletC)).orElseThrow();
        var reloadedD = walletRepository.findById(UUID.fromString(walletD)).orElseThrow();

        // A, B hoàn tác nguyên trạng ban đầu.
        assertThat(reloadedA.getCurrentBalance()).isEqualTo(1_000_000);
        assertThat(reloadedB.getCurrentBalance()).isEqualTo(500_000);
        // C bị trừ 150.000, D được cộng 150.000.
        assertThat(reloadedC.getCurrentBalance()).isEqualTo(700_000 - 150000);
        assertThat(reloadedD.getCurrentBalance()).isEqualTo(300_000 + 150000);
    }
}
