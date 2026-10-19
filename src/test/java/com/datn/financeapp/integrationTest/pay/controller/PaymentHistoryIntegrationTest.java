package com.datn.financeapp.integrationTest.pay.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.repository.PaymentRepository;
import com.datn.financeapp.module.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class PaymentHistoryIntegrationTest {

    private static final String PASSWORD = "matkhau123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1wYXltZW50LWhpc3RvcnktdGVzdDM=");
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
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestAuthSupport authSupport;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM notifications");
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM wallets");
        userRepository.deleteAll();
    }

    private UUID userIdOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private Payment savePayment(UUID userId, PaymentStatus status, long daysAgo) {
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .id(UUID.randomUUID())
                .orderCode(paymentRepository.nextOrderCode())
                .userId(userId)
                .type(PaymentType.PREMIUM_UPGRADE)
                .referenceId("PREMIUM_1M")
                .amount(49_000L)
                .status(status)
                .payosLinkId("link")
                .expiresAt(Instant.now().minusSeconds(60))
                .build());
        jdbcTemplate.update("UPDATE payments SET created_at = now() - (? * interval '1 day') WHERE order_code = ?",
                daysAgo, payment.getOrderCode());
        return payment;
    }

    // Gán ROLE_ADMIN rồi đăng nhập lại để token mang quyền admin.
    private String adminToken(String email) throws Exception {
        authSupport.registerAndGetAccessToken(email);
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) SELECT ?, r.id FROM roles r WHERE r.name = 'ROLE_ADMIN' ON CONFLICT DO NOTHING",
                userIdOf(email));
        return authSupport.loginAndGetAccessToken(email, PASSWORD);
    }

    @Test
    @DisplayName("GET /payments: chỉ thấy đơn của chính mình, mới nhất trước, có meta phân trang")
    void list_OnlyOwnPayments_NewestFirst() throws Exception {
        String email = "history.me@example.com";
        String token = authSupport.registerAndGetAccessToken(email);
        authSupport.registerAndGetAccessToken("history.other@example.com");
        Payment older = savePayment(userIdOf(email), PaymentStatus.EXPIRED, 5);
        Payment newer = savePayment(userIdOf(email), PaymentStatus.PAID, 1);
        savePayment(userIdOf("history.other@example.com"), PaymentStatus.PAID, 2);

        mockMvc.perform(get("/payments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].order_code").value(newer.getOrderCode()))
                .andExpect(jsonPath("$.data.items[1].order_code").value(older.getOrderCode()))
                .andExpect(jsonPath("$.data.meta.total_items").value(2))
                .andExpect(jsonPath("$.data.meta.page").value(1));
    }

    @Test
    @DisplayName("GET /payments?status=PAID: lọc theo trạng thái")
    void list_FilterByStatus() throws Exception {
        String email = "history.filter@example.com";
        String token = authSupport.registerAndGetAccessToken(email);
        savePayment(userIdOf(email), PaymentStatus.EXPIRED, 5);
        Payment paid = savePayment(userIdOf(email), PaymentStatus.PAID, 1);

        mockMvc.perform(get("/payments").param("status", "PAID").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].order_code").value(paid.getOrderCode()));
    }

    @Test
    @DisplayName("GET /payments không có token -> 401")
    void list_WithoutToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/payments")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /payments/admin: user thường không có PAYMENT_READ -> 403")
    void adminList_NormalUser_Forbidden() throws Exception {
        String token = authSupport.registerAndGetAccessToken("history.normal@example.com");

        mockMvc.perform(get("/payments/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/payments/admin/123").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /payments/admin: admin thấy đơn của mọi user và lọc được theo user_id")
    void adminList_AdminSeesAll_AndFiltersByUser() throws Exception {
        String adminToken = adminToken("history.admin@example.com");
        authSupport.registerAndGetAccessToken("history.u1@example.com");
        authSupport.registerAndGetAccessToken("history.u2@example.com");
        UUID u1 = userIdOf("history.u1@example.com");
        savePayment(u1, PaymentStatus.PAID, 1);
        savePayment(userIdOf("history.u2@example.com"), PaymentStatus.EXPIRED, 2);

        mockMvc.perform(get("/payments/admin").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.meta.total_items").value(2));

        mockMvc.perform(get("/payments/admin").param("user_id", u1.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].user_id").value(u1.toString()))
                .andExpect(jsonPath("$.data.items[0].payos_link_id").value("link"));
    }

    @Test
    @DisplayName("GET /payments/admin/{order_code}: admin xem được đơn của người khác; không có đơn -> 404")
    void adminDetail_FoundAndNotFound() throws Exception {
        String adminToken = adminToken("history.admin2@example.com");
        authSupport.registerAndGetAccessToken("history.owner@example.com");
        Payment payment = savePayment(userIdOf("history.owner@example.com"), PaymentStatus.PAID, 1);

        mockMvc.perform(get("/payments/admin/" + payment.getOrderCode())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order_code").value(payment.getOrderCode()))
                .andExpect(jsonPath("$.data.status").value("PAID"));

        mockMvc.perform(get("/payments/admin/1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
