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
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.pay.exceptions.PaymentException;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
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

/**
 * Dashboard webhook: mỗi lần payOS gọi webhook đều để lại một dòng nhật ký, kể cả khi bị từ chối hoặc rollback,
 * và chỉ quản trị viên xem được qua API.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class PaymentWebhookLogIntegrationTest {

    private static final long PRICE_1M = 49_000L;
    private static final String PASSWORD = "matkhau123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1wYXltZW50LXdlYmhvb2stbG9nLXRlc3Q=");
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
        jdbcTemplate.update("DELETE FROM payment_webhook_logs");
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
        lenient().when(data.getOrderCode()).thenReturn(orderCode);
        lenient().when(data.getAmount()).thenReturn(amount);
        lenient().when(data.getCode()).thenReturn("00");
        lenient().when(data.getPaymentLinkId()).thenReturn("link-test");
        when(payOsClientHelper.verifyWebhook(any())).thenReturn(data);
    }

    private String webhookJson(long orderCode, long amount) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "code", "00", "desc", "success", "success", true, "signature", "sig",
                "data", Map.of("orderCode", orderCode, "amount", amount, "code", "00", "paymentLinkId", "link-test")));
    }

    private int postWebhook(String body) throws Exception {
        return mockMvc.perform(post("/public/payments/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private List<Map<String, Object>> logs() {
        return jdbcTemplate.queryForList("SELECT * FROM payment_webhook_logs ORDER BY received_at");
    }

    private String adminToken(String email) throws Exception {
        authSupport.registerAndGetAccessToken(email);
        UUID id = userRepository.findByEmail(email).orElseThrow().getId();
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) SELECT ?, r.id FROM roles r WHERE r.name = 'ROLE_ADMIN' ON CONFLICT DO NOTHING",
                id);
        return authSupport.loginAndGetAccessToken(email, PASSWORD);
    }

    @Test
    @DisplayName("webhook hợp lệ -> một dòng PROCESSED (HTTP 200, có order_code, số tiền, IP); gửi lại -> ALREADY_PAID")
    void validWebhook_LoggedAsProcessedThenAlreadyPaid() throws Exception {
        String token = authSupport.registerAndGetAccessToken("log.ok@example.com");
        long orderCode = createPendingOrder(token);
        stubVerified(orderCode, PRICE_1M);

        assertThat(postWebhook(webhookJson(orderCode, PRICE_1M))).isEqualTo(200);
        assertThat(postWebhook(webhookJson(orderCode, PRICE_1M))).isEqualTo(200);

        List<Map<String, Object>> rows = logs();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("result")).isEqualTo("PROCESSED");
        assertThat(((Number) rows.get(0).get("order_code")).longValue()).isEqualTo(orderCode);
        assertThat(((Number) rows.get(0).get("amount")).longValue()).isEqualTo(PRICE_1M);
        assertThat(rows.get(0).get("http_status")).isEqualTo(200);
        assertThat(rows.get(0).get("client_ip")).isNotNull();
        assertThat(((Number) rows.get(0).get("duration_ms")).intValue()).isGreaterThanOrEqualTo(0);
        assertThat(rows.get(1).get("result")).isEqualTo("ALREADY_PAID");
    }

    @Test
    @DisplayName("sai chữ ký -> HTTP 400 NHƯNG vẫn có dòng INVALID_SIGNATURE (kèm order_code do bên gửi khai)")
    void invalidSignature_StillLogged() throws Exception {
        when(payOsClientHelper.verifyWebhook(any())).thenThrow(new PaymentException(ErrorCode.PAYMENT_WEBHOOK_INVALID));

        assertThat(postWebhook(webhookJson(200_777L, PRICE_1M))).isEqualTo(400);

        List<Map<String, Object>> rows = logs();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("result")).isEqualTo("INVALID_SIGNATURE");
        assertThat(rows.get(0).get("http_status")).isEqualTo(400);
        assertThat(((Number) rows.get(0).get("order_code")).longValue()).isEqualTo(200_777L);
    }

    @Test
    @DisplayName("body không phải JSON -> HTTP 400 và dòng INVALID_BODY")
    void malformedBody_LoggedAsInvalidBody() throws Exception {
        assertThat(postWebhook("{not json")).isEqualTo(400);

        List<Map<String, Object>> rows = logs();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("result")).isEqualTo("INVALID_BODY");
        assertThat(rows.get(0).get("order_code")).isNull();
    }

    @Test
    @DisplayName("order_code lạ trong dải của hệ thống -> HTTP 200 và dòng ORDER_NOT_FOUND; mã nhỏ (webhook thử) -> IGNORED_EMPTY")
    void unknownOrder_LoggedByRange() throws Exception {
        stubVerified(900_000L, PRICE_1M);
        assertThat(postWebhook(webhookJson(900_000L, PRICE_1M))).isEqualTo(200);
        stubVerified(123L, PRICE_1M);
        assertThat(postWebhook(webhookJson(123L, PRICE_1M))).isEqualTo(200);

        List<Map<String, Object>> rows = logs();
        assertThat(rows).extracting(r -> r.get("result")).containsExactly("ORDER_NOT_FOUND", "IGNORED_EMPTY");
    }

    @Test
    @DisplayName("xử lý bị rollback (nâng cấp lỗi) -> HTTP 500, đơn vẫn PENDING, nhưng dòng ERROR VẪN được lưu (transaction riêng)")
    void rollbackStillLogged() throws Exception {
        String token = authSupport.registerAndGetAccessToken("log.rollback@example.com");
        long orderCode = createPendingOrder(token);
        // referenceId không hợp lệ làm listener nâng cấp ném lỗi -> cả transaction webhook rollback
        jdbcTemplate.update("UPDATE payments SET reference_id = 'PREMIUM_99M' WHERE order_code = ?", orderCode);
        stubVerified(orderCode, PRICE_1M);

        assertThat(postWebhook(webhookJson(orderCode, PRICE_1M))).isEqualTo(500);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_code = ?", String.class, orderCode))
                .isEqualTo("PENDING");
        List<Map<String, Object>> rows = logs();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("result")).isEqualTo("ERROR");
        assertThat(rows.get(0).get("http_status")).isEqualTo(500);
    }

    @Test
    @DisplayName("API admin: không token -> 401, user thường -> 403, admin -> 200 và chỉ lỗi khi only_failed=true")
    void adminApi_Authorization_AndFiltering() throws Exception {
        String userToken = authSupport.registerAndGetAccessToken("log.user@example.com");
        String adminToken = adminToken("log.admin@example.com");
        long orderCode = createPendingOrder(userToken);
        stubVerified(orderCode, PRICE_1M);
        postWebhook(webhookJson(orderCode, PRICE_1M));
        postWebhook("{not json");

        mockMvc.perform(get("/payments/admin/webhook-logs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/payments/admin/webhook-logs").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/payments/admin/webhook-logs/summary").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/payments/admin/webhook-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.meta.total_items").value(2));
        mockMvc.perform(get("/payments/admin/webhook-logs").param("only_failed", "true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].result").value("INVALID_BODY"));
        mockMvc.perform(get("/payments/admin/webhook-logs").param("result", "PROCESSED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].order_code").value(orderCode));
    }

    @Test
    @DisplayName("summary: đếm đúng theo loại, tỉ lệ lỗi; không vướng đường /payments/admin/{order_code}")
    void summary_CountsByResult() throws Exception {
        String userToken = authSupport.registerAndGetAccessToken("sum.user@example.com");
        String adminToken = adminToken("sum.admin@example.com");
        long orderCode = createPendingOrder(userToken);
        stubVerified(orderCode, PRICE_1M);
        postWebhook(webhookJson(orderCode, PRICE_1M));
        postWebhook("{bad");
        postWebhook("{bad2");

        mockMvc.perform(get("/payments/admin/webhook-logs/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.failed").value(2))
                .andExpect(jsonPath("$.data.counts_by_result.PROCESSED").value(1))
                .andExpect(jsonPath("$.data.counts_by_result.INVALID_BODY").value(2))
                .andExpect(jsonPath("$.data.last_success_at").isNotEmpty())
                .andExpect(jsonPath("$.data.last_failure_at").isNotEmpty())
                .andExpect(jsonPath("$.data.paid_without_webhook.length()").value(0));
    }

    @Test
    @DisplayName("summary: đơn PAID qua polling, quá ngưỡng 10 phút, không có webhook -> bị liệt kê; đơn có webhook -> không")
    void summary_PaidWithoutWebhook_Listed() throws Exception {
        String userToken = authSupport.registerAndGetAccessToken("miss.user@example.com");
        String adminToken = adminToken("miss.admin@example.com");

        // đơn A: được xác nhận qua polling (payOS báo đã trả), KHÔNG có webhook
        long orderA = createPendingOrder(userToken);
        PaymentLink paid = mock(PaymentLink.class);
        when(paid.getStatus()).thenReturn(PaymentLinkStatus.PAID);
        when(paid.getAmount()).thenReturn(PRICE_1M);
        when(paid.getAmountPaid()).thenReturn(PRICE_1M);
        when(payOsClientHelper.getLink(orderA)).thenReturn(paid);
        mockMvc.perform(get("/payments/" + orderA).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));

        // đơn B: có webhook PROCESSED. User đã premium nên phải tạo user khác
        String otherToken = authSupport.registerAndGetAccessToken("miss.other@example.com");
        long orderB = createPendingOrder(otherToken);
        stubVerified(orderB, PRICE_1M);
        postWebhook(webhookJson(orderB, PRICE_1M));

        // vừa PAID chưa quá 10 phút -> chưa bị coi là thiếu webhook
        mockMvc.perform(get("/payments/admin/webhook-logs/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.paid_without_webhook.length()").value(0));

        // lùi paid_at của cả hai đơn về 30 phút trước
        jdbcTemplate.update("UPDATE payments SET paid_at = now() - interval '30 minutes' WHERE order_code IN (?, ?)", orderA, orderB);

        mockMvc.perform(get("/payments/admin/webhook-logs/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paid_without_webhook.length()").value(1))
                .andExpect(jsonPath("$.data.paid_without_webhook[0]").value(orderA))
                .andExpect(jsonPath("$.data.missing_minutes").value(10));
    }
}
