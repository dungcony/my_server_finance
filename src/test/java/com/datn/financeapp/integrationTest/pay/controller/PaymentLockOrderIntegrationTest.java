package com.datn.financeapp.integrationTest.pay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class PaymentLockOrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1wYXltZW50LWxvY2stb3JkZXItdGVzdA==");
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

    @MockitoBean
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
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanAndStub() {
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM notifications");
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM wallets");
        userRepository.deleteAll();

        reset(payOsClientHelper);
        CreatePaymentLinkResponse link = mock(CreatePaymentLinkResponse.class);
        lenient().when(link.getPaymentLinkId()).thenReturn("link-test");
        lenient().when(link.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/link-test");
        when(payOsClientHelper.createLink(anyLong(), anyLong(), any(), anyLong())).thenReturn(link);
    }

    private long createPendingOrder(String token) throws Exception {
        String body = mockMvc.perform(post("/users/me/premium-upgrades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("plan_code", "PREMIUM_1M"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
        return ((Number) data.get("order_code")).longValue();
    }

    @Test
    @DisplayName("R13: Khi đơn PENDING đang bị transaction khác khoá FOR UPDATE -> createPayment NOWAIT trả 409 REQUEST_IN_PROGRESS")
    void createPayment_WhenPendingLocked_Returns409() throws Exception {
        String token = authSupport.registerAndGetAccessToken("lock.contention@example.com");
        long orderCode = createPendingOrder(token);

        TransactionTemplate tt = new TransactionTemplate(transactionManager);

        // Giữ khoá FOR UPDATE trong thread test
        tt.execute(status -> {
            jdbcTemplate.queryForList("SELECT id FROM payments WHERE order_code = ? FOR UPDATE", orderCode);

            // Trong khi đang giữ khoá, request HTTP createPayment tới và gặp NOWAIT contention
            try {
                mockMvc.perform(post("/users/me/premium-upgrades")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("plan_code", "PREMIUM_6M"))))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.error.code").value("REQUEST_IN_PROGRESS"));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });

        // Sau khi nhả khoá, gọi lại thành công
        PaymentLink linkMock = mock(PaymentLink.class);
        when(linkMock.getStatus()).thenReturn(PaymentLinkStatus.PENDING);
        when(payOsClientHelper.getLink(orderCode)).thenReturn(linkMock);
        PaymentLink cancelMock = mock(PaymentLink.class);
        when(cancelMock.getStatus()).thenReturn(PaymentLinkStatus.CANCELLED);
        when(payOsClientHelper.cancelLink(orderCode)).thenReturn(cancelMock);

        mockMvc.perform(post("/users/me/premium-upgrades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("plan_code", "PREMIUM_6M"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reference_id").value("PREMIUM_6M"));
    }
}
