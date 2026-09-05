package com.datn.financeapp.auth;

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
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test tích hợp qua HTTP thật (MockMvc + AuthController + AuthService + Testcontainers
 * PostgreSQL) cho AUTH-01 (register + ví Tiền mặt trong 1 transaction) và AUTH-02 (login,
 * sai email/sai password cùng mã lỗi INVALID_CREDENTIALS).
 *
 * RateLimitFilter thật (5/phút/IP trên /auth/**, đã verify riêng ở RateLimitFilterTest của
 * Plan 03) bị thay bằng no-op qua @TestConfiguration @Primary — nhiều test case trong file này
 * gọi /auth/register|login hơn 5 lần trong cùng 1 JVM run (bucket in-memory theo IP dùng
 * chung), nếu giữ filter thật sẽ bị 429 giả (không phải lỗi nghiệp vụ đang test).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRegisterLoginIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hdXRoLXJlZ2lzdGVyLXRlc3QtMzJi");
    }

    @TestConfiguration
    static class NoRateLimitConfig {
        // Tên method PHẢI khớp "rateLimitFilter" (tên field @RequiredArgsConstructor autowire
        // trong SecurityConfig) — Spring ưu tiên khớp TÊN bean trước @Primary khi có nhiều
        // ứng viên cùng type, nên chỉ @Primary không đủ để ghi đè bean thật cùng tên mặc định.
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

    @BeforeEach
    void cleanTables() {
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void register_succeeds_createsUserAndCashWalletWithZeroBalanceInSameTransaction() throws Exception {
        Map<String, Object> body = Map.of(
                "email", "minh.nguyen@example.com",
                "password", "matkhau123",
                "username", "Minh Nguyễn");

        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.id").exists())
                // B4 — ba trường mới, canh đúng KHOÁ JSON chứ không chỉ giá trị: field record tên
                // isConfirm khiến Jackson mặc định sinh khoá "confirm", phải có @JsonProperty mới
                // ra "is_confirm" như api/01 chốt. Test ở tầng service không nhìn thấy sai lệch này.
                .andExpect(jsonPath("$.data.user.username").value("Minh Nguyễn"))
                .andExpect(jsonPath("$.data.user.is_confirm").value(false))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.refresh_token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        Map<?, ?> user = (Map<?, ?>) data.get("user");
        String userId = (String) user.get("id");

        var wallets = walletRepository.findAll().stream()
                .filter(w -> w.getUserId() != null && w.getUserId().toString().equals(userId))
                .toList();

        assertThat(wallets).hasSize(1);
        assertThat(wallets.get(0).getName()).isEqualTo("Tiền mặt");
        assertThat(wallets.get(0).getCurrentBalance()).isEqualTo(0L);
    }

    @Test
    void register_emailAlreadyExists_returns409EmailAlreadyExists() throws Exception {
        Map<String, Object> body = Map.of(
                "email", "trung.lap@example.com",
                "password", "matkhau123",
                "username", "Người Trùng");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void login_wrongPasswordAndWrongEmail_bothReturnSameErrorCodeAndMessage() throws Exception {
        Map<String, Object> registerBody = Map.of(
                "email", "dang.nhap@example.com",
                "password", "matkhaudung1",
                "username", "Người Đăng Nhập");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody)))
                .andExpect(status().isCreated());

        Map<String, Object> wrongPassword = Map.of("email", "dang.nhap@example.com", "password", "sairoi123");
        String responseWrongPassword = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> wrongEmail = Map.of("email", "khong.ton.tai@example.com", "password", "khoiquantam1");
        String responseWrongEmail = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongEmail)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String messageWrongPassword = (String) ((Map<?, ?>) ((Map<?, ?>) objectMapper.readValue(responseWrongPassword, Map.class)
                .get("error"))).get("message");
        String messageWrongEmail = (String) ((Map<?, ?>) ((Map<?, ?>) objectMapper.readValue(responseWrongEmail, Map.class)
                .get("error"))).get("message");

        assertThat(messageWrongPassword).isEqualTo(messageWrongEmail);
    }

    @Test
    void after5FailedAttempts_6thLoginReturnsAccountLocked() throws Exception {
        Map<String, Object> registerBody = Map.of(
                "email", "bi.khoa@example.com",
                "password", "matkhaudung1",
                "username", "Người Bị Khoá");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody)))
                .andExpect(status().isCreated());

        Map<String, Object> wrongPassword = Map.of("email", "bi.khoa@example.com", "password", "sai-mat-khau");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongPassword)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
    }
}
