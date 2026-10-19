package com.datn.financeapp.integrationTest.pay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.entity.PaymentWebhookLog;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import com.datn.financeapp.module.pay.repository.PaymentRepository;
import com.datn.financeapp.module.pay.repository.PaymentWebhookLogRepository;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Transactional
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class PaymentWebhookLogRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci13ZWJob29rLWxvZy1yZXBvLXRlc3Q=");
    }

    @Autowired
    private PaymentWebhookLogRepository logRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    private PaymentWebhookLog log(WebhookResult result, Long orderCode, Instant at, int occurrences) {
        return logRepository.saveAndFlush(PaymentWebhookLog.builder()
                .receivedAt(at).orderCode(orderCode).result(result).httpStatus(200).durationMs(3)
                .occurrences(occurrences).aggregated(occurrences > 1).build());
    }

    private UUID saveUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                        .id(UUID.randomUUID()).email(email).password("hash").firstName("W").lastName("L")
                        .plan(UserPlan.FREE).status(UserStatus.ACTIVE).build())
                .getId();
    }

    private Payment savePayment(UUID userId, PaymentStatus status, Instant paidAt) {
        return paymentRepository.saveAndFlush(Payment.builder()
                .id(UUID.randomUUID()).orderCode(paymentRepository.nextOrderCode()).userId(userId)
                .type(PaymentType.PREMIUM_UPGRADE).referenceId("PREMIUM_1M").amount(49_000L)
                .status(status).payosLinkId("link").expiresAt(Instant.now().plusSeconds(600)).paidAt(paidAt).build());
    }

    @Test
    @DisplayName("deleteOlderThan: chỉ xoá dòng cũ hơn mốc, giữ dòng mới")
    void deleteOlderThan_OnlyOld() {
        Instant now = Instant.now();
        log(WebhookResult.PROCESSED, 1L, now.minus(40, ChronoUnit.DAYS), 1);
        PaymentWebhookLog recent = log(WebhookResult.PROCESSED, 2L, now.minus(1, ChronoUnit.DAYS), 1);

        int deleted = logRepository.deleteOlderThan(now.minus(30, ChronoUnit.DAYS));

        assertThat(deleted).isEqualTo(1);
        assertThat(logRepository.findAll()).extracting(PaymentWebhookLog::getId).containsExactly(recent.getId());
    }

    @Test
    @DisplayName("countGroupedByResult: cộng cả số request đã gộp (occurrences), chỉ tính trong khoảng thời gian")
    void countGroupedByResult_SumsOccurrences() {
        Instant now = Instant.now();
        log(WebhookResult.INVALID_SIGNATURE, null, now.minusSeconds(60), 1);
        log(WebhookResult.INVALID_SIGNATURE, null, now.minusSeconds(30), 40);
        log(WebhookResult.PROCESSED, 5L, now.minusSeconds(10), 1);
        log(WebhookResult.PROCESSED, 6L, now.minus(5, ChronoUnit.DAYS), 1);

        List<PaymentWebhookLogRepository.ResultCount> rows = logRepository.countGroupedByResult(now.minus(1, ChronoUnit.HOURS));

        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(r -> r.getResult() == WebhookResult.INVALID_SIGNATURE)
                .extracting(PaymentWebhookLogRepository.ResultCount::getTotal).containsExactly(41L);
        assertThat(rows).filteredOn(r -> r.getResult() == WebhookResult.PROCESSED)
                .extracting(PaymentWebhookLogRepository.ResultCount::getTotal).containsExactly(1L);
    }

    @Test
    @DisplayName("lastReceivedAt: lần gần nhất của đúng nhóm kết quả; không có thì null")
    void lastReceivedAt_PerGroup() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        log(WebhookResult.PROCESSED, 1L, now.minusSeconds(100), 1);
        log(WebhookResult.ALREADY_PAID, 1L, now.minusSeconds(50), 1);
        log(WebhookResult.UNDERPAID, 2L, now.minusSeconds(10), 1);

        assertThat(logRepository.lastReceivedAt(WebhookResult.confirmations())).isEqualTo(now.minusSeconds(50));
        assertThat(logRepository.lastReceivedAt(WebhookResult.failures())).isEqualTo(now.minusSeconds(10));
        assertThat(logRepository.lastReceivedAt(List.of(WebhookResult.ERROR))).isNull();
    }

    @Test
    @DisplayName("findPaidWithoutWebhook: chỉ đơn PAID trong khoảng, quá ngưỡng và KHÔNG có webhook PROCESSED/ALREADY_PAID")
    void findPaidWithoutWebhook_Semantics() {
        Instant now = Instant.now();
        Instant since = now.minus(24, ChronoUnit.HOURS);
        Instant paidBefore = now.minus(10, ChronoUnit.MINUTES);
        Instant oldPaid = now.minus(30, ChronoUnit.MINUTES);

        Payment missing = savePayment(saveUser("w1@example.com"), PaymentStatus.PAID, oldPaid);
        Payment withWebhook = savePayment(saveUser("w2@example.com"), PaymentStatus.PAID, oldPaid);
        log(WebhookResult.PROCESSED, withWebhook.getOrderCode(), oldPaid, 1);
        Payment onlyErrorLog = savePayment(saveUser("w3@example.com"), PaymentStatus.PAID, oldPaid);
        log(WebhookResult.ERROR, onlyErrorLog.getOrderCode(), oldPaid, 1);
        savePayment(saveUser("w4@example.com"), PaymentStatus.PAID, now.minus(2, ChronoUnit.MINUTES));
        savePayment(saveUser("w5@example.com"), PaymentStatus.PENDING, null);
        savePayment(saveUser("w6@example.com"), PaymentStatus.PAID, now.minus(48, ChronoUnit.HOURS));

        List<Long> result = logRepository.findPaidWithoutWebhook(since, paidBefore,
                WebhookResult.confirmations(), PageRequest.of(0, 50));

        assertThat(result).containsExactlyInAnyOrder(missing.getOrderCode(), onlyErrorLog.getOrderCode());
    }
}
