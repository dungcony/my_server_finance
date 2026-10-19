package com.datn.financeapp.integrationTest.pay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class PaymentCancelIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1wYXltZW50LWNhbmNlbC10ZXN0MQ==");
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
    @DisplayName("DELETE /payments/{order_code}: huỷ đơn PENDING của mình -> 200 CANCELLED, DB cập nhật")
    void cancel_MyPendingOrder_Succeeds() throws Exception {
        String token = authSupport.registerAndGetAccessToken("cancel.me@example.com");
        long orderCode = createPendingOrder(token);

        PaymentLink getLinkMock = mock(PaymentLink.class);
        when(getLinkMock.getStatus()).thenReturn(PaymentLinkStatus.PENDING);
        when(payOsClientHelper.getLink(orderCode)).thenReturn(getLinkMock);

        PaymentLink cancelMock = mock(PaymentLink.class);
        when(cancelMock.getStatus()).thenReturn(PaymentLinkStatus.CANCELLED);
        when(payOsClientHelper.cancelLink(orderCode)).thenReturn(cancelMock);

        mockMvc.perform(delete("/payments/" + orderCode)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.order_code").value(orderCode))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(payOsClientHelper).cancelLink(orderCode);
        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE order_code = ?", String.class, orderCode);
        assertThat(dbStatus).isEqualTo("CANCELLED");

        // Sau khi huỷ, có thể tạo ngay đơn mới
        long newOrderCode = createPendingOrder(token);
        assertThat(newOrderCode).isNotEqualTo(orderCode);
    }

    @Test
    @DisplayName("DELETE /payments/{order_code}: đơn của user khác -> 404 PAYMENT_NOT_FOUND")
    void cancel_OtherUserOrder_NotFound() throws Exception {
        String tokenA = authSupport.registerAndGetAccessToken("user.a@example.com");
        String tokenB = authSupport.registerAndGetAccessToken("user.b@example.com");
        long orderCodeA = createPendingOrder(tokenA);

        mockMvc.perform(delete("/payments/" + orderCodeA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_FOUND"));

        verify(payOsClientHelper, never()).cancelLink(anyLong());
    }

    @Test
    @DisplayName("DELETE /payments/{order_code}: không có token -> 401 UNAUTHORIZED")
    void cancel_NoToken_Unauthorized() throws Exception {
        mockMvc.perform(delete("/payments/100001"))
                .andExpect(status().isUnauthorized());
    }
}
