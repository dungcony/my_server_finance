package com.datn.financeapp.integrationTest.pay.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.repository.PaymentRepository;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
class PaymentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1wYXltZW50LXJlcG8tdGVzdC0zMmI=");
    }

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID saveUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                        .id(UUID.randomUUID())
                        .email(email)
                        .password("hash")
                        .firstName("Pay")
                        .lastName("Tester")
                        .plan(UserPlan.FREE)
                        .status(UserStatus.ACTIVE)
                        .build())
                .getId();
    }

    private Payment savePayment(UUID userId, PaymentStatus status, Instant expiresAt) {
        return paymentRepository.saveAndFlush(Payment.builder()
                .id(UUID.randomUUID())
                .orderCode(paymentRepository.nextOrderCode())
                .userId(userId)
                .type(PaymentType.PREMIUM_UPGRADE)
                .referenceId("PREMIUM_1M")
                .amount(49_000L)
                .status(status)
                .payosLinkId("link")
                .expiresAt(expiresAt)
                .build());
    }

    @Test
    @DisplayName("unique index: hai đơn PENDING cùng user cùng loại -> vi phạm ràng buộc")
    void twoPendingSameUserSameType_ViolatesUniqueIndex() {
        UUID userId = saveUser("pay.dup@example.com");
        savePayment(userId, PaymentStatus.PENDING, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> savePayment(userId, PaymentStatus.PENDING, Instant.now().plusSeconds(600)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("unique index: đơn cũ đã EXPIRED/PAID thì vẫn tạo được một đơn PENDING mới")
    void finishedOrdersDoNotBlockNewPending() {
        UUID userId = saveUser("pay.ok@example.com");
        savePayment(userId, PaymentStatus.EXPIRED, Instant.now().minusSeconds(600));
        savePayment(userId, PaymentStatus.PAID, Instant.now().minusSeconds(600));

        Payment pending = savePayment(userId, PaymentStatus.PENDING, Instant.now().plusSeconds(600));

        assertThat(paymentRepository.findByUserIdAndTypeAndStatus(
                userId, PaymentType.PREMIUM_UPGRADE, PaymentStatus.PENDING))
                .get().extracting(Payment::getOrderCode).isEqualTo(pending.getOrderCode());
    }

    @Test
    @DisplayName("unique index: hai user khác nhau đều có đơn PENDING được")
    void pendingOfDifferentUsers_Allowed() {
        savePayment(saveUser("pay.a@example.com"), PaymentStatus.PENDING, Instant.now().plusSeconds(600));
        savePayment(saveUser("pay.b@example.com"), PaymentStatus.PENDING, Instant.now().plusSeconds(600));

        assertThat(paymentRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("findOverdueOrderCodes: chỉ trả đơn PENDING đã quá hạn")
    void findOverdueOrderCodes_OnlyPendingAndOverdue() {
        Payment overdue = savePayment(saveUser("pay.over@example.com"), PaymentStatus.PENDING, Instant.now().minusSeconds(60));
        savePayment(saveUser("pay.future@example.com"), PaymentStatus.PENDING, Instant.now().plusSeconds(600));
        savePayment(saveUser("pay.expired@example.com"), PaymentStatus.EXPIRED, Instant.now().minusSeconds(60));
        savePayment(saveUser("pay.paid@example.com"), PaymentStatus.PAID, Instant.now().minusSeconds(60));

        List<Long> result = paymentRepository.findOverdueOrderCodes(
                PaymentStatus.PENDING, Instant.now(), PageRequest.of(0, 10));

        assertThat(result).containsExactly(overdue.getOrderCode());
    }

    @Test
    @DisplayName("findOverdueOrderCodes: giới hạn theo kích thước batch")
    void findOverdueOrderCodes_RespectsLimit() {
        savePayment(saveUser("pay.l1@example.com"), PaymentStatus.PENDING, Instant.now().minusSeconds(60));
        savePayment(saveUser("pay.l2@example.com"), PaymentStatus.PENDING, Instant.now().minusSeconds(60));
        savePayment(saveUser("pay.l3@example.com"), PaymentStatus.PENDING, Instant.now().minusSeconds(60));

        List<Long> result = paymentRepository.findOverdueOrderCodes(
                PaymentStatus.PENDING, Instant.now(), PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("nextOrderCode: tăng dần và không nhỏ hơn mốc khởi đầu của sequence")
    void nextOrderCode_Increasing() {
        long first = paymentRepository.nextOrderCode();
        long second = paymentRepository.nextOrderCode();

        assertThat(first).isGreaterThanOrEqualTo(100_000L);
        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("findByOrderCodeAndUserId: đơn của user khác -> rỗng")
    void findByOrderCodeAndUserId_OtherUser_Empty() {
        UUID owner = saveUser("pay.owner@example.com");
        UUID other = saveUser("pay.other@example.com");
        Payment payment = savePayment(owner, PaymentStatus.PENDING, Instant.now().plusSeconds(600));

        assertThat(paymentRepository.findByOrderCodeAndUserId(payment.getOrderCode(), other)).isEmpty();
        assertThat(paymentRepository.findByOrderCodeAndUserId(payment.getOrderCode(), owner)).isPresent();
    }

    @Test
    @DisplayName("findByOrderCodeForUpdate: đọc được đơn trong transaction (khoá ghi)")
    void findByOrderCodeForUpdate_Found() {
        Payment payment = savePayment(saveUser("pay.lock@example.com"), PaymentStatus.PENDING, Instant.now().plusSeconds(600));

        assertThat(paymentRepository.findByOrderCodeForUpdate(payment.getOrderCode())).isPresent();
        assertThat(paymentRepository.findByOrderCodeForUpdate(-1L)).isEmpty();
    }

    @Test
    @DisplayName("existsByOrderCodeAndUserId: trả true khi đúng chủ sở hữu, false khi khác chủ hoặc không tồn tại")
    void existsByOrderCodeAndUserId_Check() {
        UUID owner = saveUser("pay.exists.owner@example.com");
        UUID other = saveUser("pay.exists.other@example.com");
        Payment payment = savePayment(owner, PaymentStatus.PENDING, Instant.now().plusSeconds(600));

        assertThat(paymentRepository.existsByOrderCodeAndUserId(payment.getOrderCode(), owner)).isTrue();
        assertThat(paymentRepository.existsByOrderCodeAndUserId(payment.getOrderCode(), other)).isFalse();
        assertThat(paymentRepository.existsByOrderCodeAndUserId(999_999L, owner)).isFalse();
    }

    @Test
    @DisplayName("findPendingForUpdateNoWait: tìm đúng đơn PENDING của user")
    void findPendingForUpdateNoWait_Check() {
        UUID user = saveUser("pay.nowait@example.com");
        Payment payment = savePayment(user, PaymentStatus.PENDING, Instant.now().plusSeconds(600));

        assertThat(paymentRepository.findPendingForUpdateNoWait(user, PaymentType.PREMIUM_UPGRADE, PaymentStatus.PENDING))
                .isPresent()
                .get()
                .extracting(Payment::getOrderCode).isEqualTo(payment.getOrderCode());

        assertThat(paymentRepository.findPendingForUpdateNoWait(user, PaymentType.PREMIUM_UPGRADE, PaymentStatus.PAID))
                .isEmpty();
    }
}
