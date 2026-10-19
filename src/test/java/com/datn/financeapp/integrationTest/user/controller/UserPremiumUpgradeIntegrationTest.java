package com.datn.financeapp.integrationTest.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class UserPremiumUpgradeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1wcmVtaXVtLXVwZ3JhZGUtdGVzdDMy");
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
        when(link.getPaymentLinkId()).thenReturn("link-test");
        when(link.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/link-test");
        when(payOsClientHelper.createLink(anyLong(), anyLong(), any(), anyLong())).thenReturn(link);
    }

    private String upgradeBody(String planCode) throws Exception {
        return objectMapper.writeValueAsString(Map.of("plan_code", planCode));
    }

    @Test
    @DisplayName("không có token -> 401")
    void upgrade_WithoutToken_Unauthorized() throws Exception {
        mockMvc.perform(post("/users/me/premium-upgrades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upgradeBody("PREMIUM_1M")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("tạo đơn: trả checkout_url, giá do server tính, lưu một dòng PENDING")
    void upgrade_CreatesPendingOrderWithServerPrice() throws Exception {
        String token = authSupport.registerAndGetAccessToken("premium.new@example.com");

        mockMvc.perform(post("/users/me/premium-upgrades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upgradeBody("PREMIUM_6M")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reference_id").value("PREMIUM_6M"))
                .andExpect(jsonPath("$.data.amount").value(249_000))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.checkout_url").value("https://pay.payos.vn/web/link-test"))
                .andExpect(jsonPath("$.data.order_code").isNumber());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE status = 'PENDING' AND amount = 249000", Integer.class))
                .isEqualTo(1);
        verify(payOsClientHelper).createLink(anyLong(), eq(249_000L), any(), anyLong());
    }

    @Test
    @DisplayName("còn đơn chờ -> tạo đơn mới: đơn cũ bị huỷ (CANCELLED), đơn mới PENDING với mã khác")
    void upgrade_WithPendingOrder_CancelsOldAndCreatesNewOrder() throws Exception {
        String token = authSupport.registerAndGetAccessToken("premium.twice@example.com");

        PaymentLink linkMock = mock(PaymentLink.class);
        when(linkMock.getStatus()).thenReturn(PaymentLinkStatus.PENDING);
        when(payOsClientHelper.getLink(anyLong())).thenReturn(linkMock);
        PaymentLink cancelMock = mock(PaymentLink.class);
        when(cancelMock.getStatus()).thenReturn(PaymentLinkStatus.CANCELLED);
        when(payOsClientHelper.cancelLink(anyLong())).thenReturn(cancelMock);

        String first = mockMvc.perform(post("/users/me/premium-upgrades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upgradeBody("PREMIUM_1M")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long firstOrderCode = orderCodeOf(first);

        String second = mockMvc.perform(post("/users/me/premium-upgrades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upgradeBody("PREMIUM_12M")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reference_id").value("PREMIUM_12M"))
                .andExpect(jsonPath("$.data.amount").value(449_000))
                .andReturn().getResponse().getContentAsString();

        long secondOrderCode = orderCodeOf(second);
        assertThat(secondOrderCode).isNotEqualTo(firstOrderCode);

        verify(payOsClientHelper).cancelLink(firstOrderCode);
        verify(payOsClientHelper, times(2)).createLink(anyLong(), anyLong(), any(), anyLong());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE order_code = ?", String.class, firstOrderCode))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE order_code = ?", String.class, secondOrderCode))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE status = 'PENDING'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("đang premium còn hạn -> 409 PREMIUM_ALREADY_ACTIVE, không tạo đơn")
    void upgrade_ActivePremium_Conflict() throws Exception {
        String email = "premium.active@example.com";
        String token = authSupport.registerAndGetAccessToken(email);
        setPremium(email, Instant.now().plusSeconds(86_400L * 10));

        mockMvc.perform(post("/users/me/premium-upgrades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upgradeBody("PREMIUM_1M")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PREMIUM_ALREADY_ACTIVE"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isZero();
    }

    @Test
    @DisplayName("premium đã hết hạn -> được mua lại")
    void upgrade_ExpiredPremium_Allowed() throws Exception {
        String email = "premium.expired@example.com";
        String token = authSupport.registerAndGetAccessToken(email);
        setPremium(email, Instant.now().minusSeconds(60));

        mockMvc.perform(post("/users/me/premium-upgrades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upgradeBody("PREMIUM_1M")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("plan_code không hợp lệ hoặc thiếu -> 400 VALIDATION_ERROR")
    void upgrade_InvalidPlanCode_BadRequest() throws Exception {
        String token = authSupport.registerAndGetAccessToken("premium.bad@example.com");

        mockMvc.perform(post("/users/me/premium-upgrades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upgradeBody("PREMIUM_99M")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/users/me/premium-upgrades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private void setPremium(String email, Instant expiresAt) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setPlan(UserPlan.PREMIUM);
        user.setPlanExpiresAt(expiresAt);
        userRepository.saveAndFlush(user);
    }

    private long orderCodeOf(String responseBody) throws Exception {
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(responseBody, Map.class).get("data");
        return ((Number) data.get("order_code")).longValue();
    }
}
