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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.pay.exceptions.PaymentException;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
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
import vn.payos.model.webhooks.WebhookData;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class PaymentWebhookIntegrationTest {

    private static final long PRICE_1M = 49_000L;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1wYXltZW50LXdlYmhvb2stdGVzdDM=");
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
    void cleanTablesAndStubPayOs() {
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

    private void stubVerified(long orderCode, long amount) {
        WebhookData data = mock(WebhookData.class);
        when(data.getOrderCode()).thenReturn(orderCode);
        when(data.getAmount()).thenReturn(amount);
        when(data.getCode()).thenReturn("00");
        lenient().when(data.getPaymentLinkId()).thenReturn("link-test");
        when(payOsClientHelper.verifyWebhook(any())).thenReturn(data);
    }

    private String webhookJson(long orderCode, long amount) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "code", "00",
                "desc", "success",
                "success", true,
                "signature", "sig",
                "data", Map.of("orderCode", orderCode, "amount", amount, "code", "00")));
    }

    private void postWebhook(long orderCode, long amount) throws Exception {
        mockMvc.perform(post("/public/payments/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookJson(orderCode, amount)))
                .andExpect(status().isOk());
    }

    private String planOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getPlan().name();
    }

    private Instant planExpiresAtOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getPlanExpiresAt();
    }

    private String statusInDb(long orderCode) {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_code = ?", String.class, orderCode);
    }

    @Test
    @DisplayName("webhook hợp lệ (không cần token): đơn thành PAID, user thành PREMIUM có hạn ~1 tháng")
    void webhook_Valid_UpgradesUser() throws Exception {
        String email = "webhook.ok@example.com";
        String token = authSupport.registerAndGetAccessToken(email);
        long orderCode = createPendingOrder(token);
        stubVerified(orderCode, PRICE_1M);

        Instant before = Instant.now();
        postWebhook(orderCode, PRICE_1M);

        assertThat(statusInDb(orderCode)).isEqualTo("PAID");
        assertThat(planOf(email)).isEqualTo(UserPlan.PREMIUM.name());
        assertThat(planExpiresAtOf(email)).isAfter(before.plusSeconds(86_400L * 27));
    }

    @Test
    @DisplayName("webhook gửi lại: hạn premium không bị cộng thêm lần hai")
    void webhook_Replayed_DoesNotExtendTwice() throws Exception {
        String email = "webhook.replay@example.com";
        String token = authSupport.registerAndGetAccessToken(email);
        long orderCode = createPendingOrder(token);
        stubVerified(orderCode, PRICE_1M);

        postWebhook(orderCode, PRICE_1M);
        Instant afterFirst = planExpiresAtOf(email);
        postWebhook(orderCode, PRICE_1M);

        assertThat(planExpiresAtOf(email)).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("webhook sai chữ ký -> 400 PAYMENT_WEBHOOK_INVALID, đơn vẫn PENDING")
    void webhook_InvalidSignature_BadRequest() throws Exception {
        String email = "webhook.sig@example.com";
        String token = authSupport.registerAndGetAccessToken(email);
        long orderCode = createPendingOrder(token);
        when(payOsClientHelper.verifyWebhook(any())).thenThrow(new PaymentException(ErrorCode.PAYMENT_WEBHOOK_INVALID));

        mockMvc.perform(post("/public/payments/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookJson(orderCode, PRICE_1M)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_WEBHOOK_INVALID"));

        assertThat(statusInDb(orderCode)).isEqualTo("PENDING");
        assertThat(planOf(email)).isEqualTo(UserPlan.FREE.name());
    }

    @Test
    @DisplayName("webhook body không phải JSON -> 400 PAYMENT_WEBHOOK_INVALID")
    void webhook_MalformedBody_BadRequest() throws Exception {
        mockMvc.perform(post("/public/payments/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_WEBHOOK_INVALID"));
    }

    @Test
    @DisplayName("webhook không có đơn tương ứng -> 200, không đổi dữ liệu (cho phép payOS đăng ký URL bằng request thử)")
    void webhook_UnknownOrder_OkNoChange() throws Exception {
        stubVerified(999_999_999L, PRICE_1M);

        postWebhook(999_999_999L, PRICE_1M);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isZero();
    }

    @Test
    @DisplayName("webhook sai số tiền -> 200 nhưng đơn vẫn PENDING, user vẫn FREE")
    void webhook_AmountMismatch_StaysPending() throws Exception {
        String email = "webhook.amount@example.com";
        String token = authSupport.registerAndGetAccessToken(email);
        long orderCode = createPendingOrder(token);
        stubVerified(orderCode, PRICE_1M - 1000);

        postWebhook(orderCode, PRICE_1M - 1000);

        assertThat(statusInDb(orderCode)).isEqualTo("PENDING");
        assertThat(planOf(email)).isEqualTo(UserPlan.FREE.name());
    }

    @Test
    @DisplayName("polling: payOS báo đã trả thì GET /payments/{order_code} trả PAID và nâng cấp user")
    void status_PaidAtPayOs_UpgradesViaPolling() throws Exception {
        String email = "poll.paid@example.com";
        String token = authSupport.registerAndGetAccessToken(email);
        long orderCode = createPendingOrder(token);
        PaymentLink paid = mock(PaymentLink.class);
        when(paid.getStatus()).thenReturn(PaymentLinkStatus.PAID);
        when(paid.getAmount()).thenReturn(PRICE_1M);
        when(payOsClientHelper.getLink(orderCode)).thenReturn(paid);

        mockMvc.perform(get("/payments/" + orderCode).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.order_code").value(orderCode));

        assertThat(planOf(email)).isEqualTo(UserPlan.PREMIUM.name());
    }

    @Test
    @DisplayName("GET /payments/{order_code} của user khác -> 404 NOT_FOUND")
    void status_OtherUsersOrder_NotFound() throws Exception {
        String ownerToken = authSupport.registerAndGetAccessToken("status.owner@example.com");
        String otherToken = authSupport.registerAndGetAccessToken("status.other@example.com");
        long orderCode = createPendingOrder(ownerToken);

        mockMvc.perform(get("/payments/" + orderCode).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("return/cancel là trang HTML công khai, không cần token")
    void returnAndCancel_PublicHtmlPages() throws Exception {
        mockMvc.perform(get("/public/payments/payos/return"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mockMvc.perform(get("/public/payments/payos/cancel"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    @DisplayName("GET /payments/{order_code} không có token -> 401")
    void status_WithoutToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/payments/123")).andExpect(status().isUnauthorized());
    }
}
