package com.datn.financeapp.integrationTest.pay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.integrationTest.pay.support.FakePayOsClient;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.pay.config.PayOsProperties;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;
import com.datn.financeapp.module.pay.scheduler.PaymentExpiryJob;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.Random;

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
import org.springframework.http.MediaType;
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
@Import({TestRedisConfig.class, TestAuthSupport.class, PaymentInvariantFuzzIntegrationTest.FakePayOsConfig.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class PaymentInvariantFuzzIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1wYXltZW50LWZ1enotdGVzdDQ=");
    }

    @TestConfiguration
    static class FakePayOsConfig {
        @Bean
        @Primary
        PayOsClientHelper payOsClientHelper(PayOsProperties properties) {
            return new FakePayOsClient(properties);
        }

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
    private PayOsClientHelper payOsClientHelper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestAuthSupport authSupport;

    @Autowired
    private PaymentExpiryJob expiryJob;

    @BeforeEach
    void clean() {
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM notifications");
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM wallets");
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Fuzz: chuỗi thao tác ngẫu nhiên -> bất biến #13 luôn được bảo toàn sau mỗi bước")
    void fuzzOperations_InvariantAlwaysPreserved() throws Exception {
        FakePayOsClient fake = (FakePayOsClient) payOsClientHelper;
        String token = authSupport.registerAndGetAccessToken("fuzz.user@example.com");

        Random random = new Random(42);
        Long lastOrderCode = null;

        for (int step = 0; step < 20; step++) {
            int op = random.nextInt(5);
            switch (op) {
                case 0 -> { // Tạo đơn
                    String plan = random.nextBoolean() ? "PREMIUM_1M" : "PREMIUM_6M";
                    try {
                        String body = mockMvc.perform(post("/users/me/premium-upgrades")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(Map.of("plan_code", plan))))
                                .andReturn().getResponse().getContentAsString();
                        Map<?, ?> json = objectMapper.readValue(body, Map.class);
                        if (Boolean.TRUE.equals(json.get("success"))) {
                            Map<?, ?> data = (Map<?, ?>) json.get("data");
                            lastOrderCode = ((Number) data.get("order_code")).longValue();
                        }
                    } catch (Exception ignored) {
                    }
                }
                case 1 -> { // User trả tiền
                    if (lastOrderCode != null) {
                        fake.userPays(lastOrderCode);
                    }
                }
                case 2 -> { // User huỷ đơn
                    if (lastOrderCode != null) {
                        try {
                            mockMvc.perform(delete("/payments/" + lastOrderCode)
                                    .header("Authorization", "Bearer " + token));
                        } catch (Exception ignored) {
                        }
                    }
                }
                case 3 -> { // Polling trạng thái
                    if (lastOrderCode != null) {
                        try {
                            mockMvc.perform(get("/payments/" + lastOrderCode)
                                    .header("Authorization", "Bearer " + token));
                        } catch (Exception ignored) {
                        }
                    }
                }
                case 4 -> { // Chạy job quá hạn
                    expiryJob.expireOverduePayments();
                }
            }

            // Kiểm tra bất biến sau mỗi bước
            Integer pendingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE status = 'PENDING'", Integer.class);
            assertThat(pendingCount).isLessThanOrEqualTo(1);

            if (lastOrderCode != null) {
                String status = jdbcTemplate.queryForObject(
                        "SELECT status FROM payments WHERE order_code = ?", String.class, lastOrderCode);
                long received = fake.getMoneyReceived(lastOrderCode);
                if ("CANCELLED".equals(status) || "EXPIRED".equals(status)) {
                    assertThat(received).isZero();
                }
            }
        }
    }
}
