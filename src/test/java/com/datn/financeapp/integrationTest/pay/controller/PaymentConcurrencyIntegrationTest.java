package com.datn.financeapp.integrationTest.pay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
class PaymentConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1wYXltZW50LWNvbmN1cnJlbmN5LXRlc3Q=");
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

        PaymentLink getLinkMock = mock(PaymentLink.class);
        lenient().when(getLinkMock.getStatus()).thenReturn(PaymentLinkStatus.PENDING);
        lenient().when(payOsClientHelper.getLink(anyLong())).thenReturn(getLinkMock);

        PaymentLink cancelMock = mock(PaymentLink.class);
        lenient().when(cancelMock.getStatus()).thenReturn(PaymentLinkStatus.CANCELLED);
        lenient().when(payOsClientHelper.cancelLink(anyLong())).thenReturn(cancelMock);
    }

    @Test
    @DisplayName("Đa luồng: 10 request tạo đơn đồng thời cùng user -> cuối cùng đúng 1 đơn PENDING, không vướng unique exception ra ngoài")
    void concurrentCreateOrders_ResultsInSinglePendingOrder() throws Exception {
        String token = authSupport.registerAndGetAccessToken("concurrent.user@example.com");

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return mockMvc.perform(post("/users/me/premium-upgrades")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("plan_code", "PREMIUM_1M"))))
                        .andReturn().getResponse().getStatus();
            }));
        }

        latch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        for (Future<Integer> f : futures) {
            int status = f.get();
            // Mỗi request hoặc 200 (thành công) hoặc 409 (REQUEST_IN_PROGRESS do khoá hoặc xung đột), không bao giờ 500
            assertThat(status).isIn(200, 409);
        }

        Integer pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE status = 'PENDING'", Integer.class);
        assertThat(pendingCount).isEqualTo(1);
    }
}
