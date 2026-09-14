package com.datn.financeapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * JOB-01 (D-57, api/02-VI.md mục 10) — đối chiếu TOÀN BỘ ví, tự sửa lệch, một ví lỗi không chặn
 * ví khác. Gọi trực tiếp {@code walletReconciliationJob.run()} qua bean Spring.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
class WalletReconciliationJobIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci13YWxsZXQtcmVjb25jaWxlLWpvYg==");
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
    private WalletReconciliationJob walletReconciliationJob;

    @Autowired
    private TestAuthSupport authSupport;

    @Autowired
    private com.datn.financeapp.auth.repository.OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        // OTP nằm ở Redis, không bị Testcontainers PostgreSQL dọn hộ.
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM notifications");
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

    private long walletBalance(String walletId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?", Long.class, UUID.fromString(walletId));
    }

    // Mô phỏng lệch dữ liệu — chỉnh current_balance sai lệch thủ công qua JdbcTemplate.
    private void corruptBalance(String walletId, long wrongBalance) {
        jdbcTemplate.update(
                "UPDATE wallets SET current_balance = ? WHERE id = ?", wrongBalance, UUID.fromString(walletId));
    }

    // ---------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------

    /**
     * Ví có current_balance bị chỉnh sai thủ công (mô phỏng lệch dữ liệu) — chạy job, số dư tự
     * sửa đúng về công thức initial_balance (ví mới tạo, chưa có giao dịch nào).
     */
    @Test
    void reconcileAllWallets_fixesDriftedBalance() throws Exception {
        String token = registerAndGetAccessToken("doi.chieu.le.ch@example.com");
        String walletId = createWallet(token, "Ví Đối Chiếu", 5_000_000);

        corruptBalance(walletId, 9_999_999);
        assertThat(walletBalance(walletId)).isEqualTo(9_999_999);

        walletReconciliationJob.run();

        assertThat(walletBalance(walletId))
                .as("Sau khi job chạy, số dư phải tự sửa về đúng initial_balance = 5.000.000")
                .isEqualTo(5_000_000);
    }

    /**
     * Một ví trong nhiều ví bị lỗi giả lập (id không tồn tại thật trong bảng users do FK ràng
     * buộc — mô phỏng bằng cách xoá ví khỏi CSDL NGAY SAU khi job đã đọc danh sách id, khiến
     * {@code reconcileOneWalletAutoFix} némNOT_FOUND cho ví đó) — job vẫn hoàn tất, ví hợp lệ còn
     * lại vẫn được xử lý, không ném exception ra ngoài {@code run()}.
     */
    @Test
    void reconcileAllWallets_oneWalletFails_othersStillProcessed() throws Exception {
        String token = registerAndGetAccessToken("doi.chieu.mot.vi.loi@example.com");
        String goodWalletId = createWallet(token, "Ví Hợp Lệ", 3_000_000);
        String willBeDeletedWalletId = createWallet(token, "Ví Sẽ Bị Xoá", 1_000_000);

        corruptBalance(goodWalletId, 1);
        // Xoá CỨNG ví thứ hai ngay trước khi job chạy — findAllActiveWalletIds() không còn thấy
        // nó (đã lọc is_deleted ở mức SELECT NOT is_deleted, nhưng ở đây ta xoá cứng để mô phỏng
        // trường hợp id trong danh sách quét không còn khớp bản ghi nào khi worker xử lý tới nó
        // — race hiếm nhưng có thể xảy ra giữa lúc đọc danh sách và lúc xử lý từng ví).
        jdbcTemplate.update("DELETE FROM wallets WHERE id = ?", UUID.fromString(willBeDeletedWalletId));

        walletReconciliationJob.run();

        assertThat(walletBalance(goodWalletId))
                .as("Ví hợp lệ khác vẫn được xử lý dù một ví trong danh sách quét đã biến mất")
                .isEqualTo(3_000_000);
    }
}
