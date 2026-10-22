package com.datn.financeapp.unitTest.pay.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.pay.config.PayOsProperties;
import com.datn.financeapp.module.pay.config.PaymentTopupProperties;
import com.datn.financeapp.module.pay.dto.res.WebhookOutcome;
import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;
import com.datn.financeapp.module.pay.mapper.PaymentMapperImpl;
import com.datn.financeapp.module.pay.repository.PaymentRepository;
import com.datn.financeapp.module.pay.service.impl.PaymentServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

/**
 * {@code processWebhook} trả {@link WebhookOutcome} để dashboard phân loại từng lần payOS gọi webhook.
 * Logic nghiệp vụ không đổi, test này chỉ khẳng định mỗi nhánh map đúng {@link WebhookResult}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentProcessWebhookOutcomeTest {

    private static final long ORDER_CODE = 200_001L;
    private static final long AMOUNT = 49_000L;
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PayOsClientHelper payOsClientHelper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        PayOsProperties properties = new PayOsProperties(
                true, "client", "key", "checksum", null, "http://return", "http://cancel", 15);
        PaymentTopupProperties topupProperties = new PaymentTopupProperties(86400, 3600, 60);
        paymentService = new PaymentServiceImpl(paymentRepository, payOsClientHelper, properties,
                topupProperties, new PaymentMapperImpl(), eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .orderCode(ORDER_CODE)
                .userId(UUID.randomUUID())
                .type(PaymentType.PREMIUM_UPGRADE)
                .referenceId("PREMIUM_1M")
                .amount(AMOUNT)
                .status(status)
                .payosLinkId("link-1")
                .expiresAt(NOW.plusSeconds(600))
                .build();
    }

    private Webhook webhook(boolean success, String code) {
        Webhook webhook = mock(Webhook.class);
        lenient().when(webhook.getSuccess()).thenReturn(success);
        lenient().when(webhook.getCode()).thenReturn(code);
        return webhook;
    }

    private WebhookData data(Long orderCode, Long amount, String code, String linkId) {
        WebhookData data = mock(WebhookData.class);
        lenient().when(data.getOrderCode()).thenReturn(orderCode);
        lenient().when(data.getAmount()).thenReturn(amount);
        lenient().when(data.getCode()).thenReturn(code);
        lenient().when(data.getPaymentLinkId()).thenReturn(linkId);
        return data;
    }

    private WebhookOutcome process(Webhook webhook, WebhookData data) {
        when(payOsClientHelper.verifyWebhook(webhook)).thenReturn(data);
        return paymentService.processWebhook(webhook);
    }

    @Test
    @DisplayName("outcome: webhook không có dữ liệu hoặc thiếu order_code -> IGNORED_EMPTY")
    void emptyData_IgnoredEmpty() {
        Webhook webhook = webhook(true, "00");
        assertThat(process(webhook, null).result()).isEqualTo(WebhookResult.IGNORED_EMPTY);

        Webhook noCode = webhook(true, "00");
        assertThat(process(noCode, data(null, AMOUNT, "00", "link-1")).result()).isEqualTo(WebhookResult.IGNORED_EMPTY);
    }

    @Test
    @DisplayName("outcome: webhook báo không thành công -> IGNORED_NOT_SUCCESS (không khoá đơn)")
    void notSuccess_IgnoredNotSuccess() {
        Webhook webhook = webhook(false, "01");

        WebhookOutcome outcome = process(webhook, data(ORDER_CODE, AMOUNT, "01", "link-1"));

        assertThat(outcome.result()).isEqualTo(WebhookResult.IGNORED_NOT_SUCCESS);
        assertThat(outcome.orderCode()).isEqualTo(ORDER_CODE);
        assertThat(outcome.result().isFailure()).isFalse();
    }

    @Test
    @DisplayName("outcome: không có đơn, order_code >= 100000 -> ORDER_NOT_FOUND (lỗi, link mồ côi?)")
    void unknownOrderOurRange_OrderNotFound() {
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.empty());

        WebhookOutcome outcome = process(webhook(true, "00"), data(ORDER_CODE, AMOUNT, "00", "link-1"));

        assertThat(outcome.result()).isEqualTo(WebhookResult.ORDER_NOT_FOUND);
        assertThat(outcome.result().isFailure()).isTrue();
    }

    @Test
    @DisplayName("outcome: không có đơn, order_code < 100000 (webhook thử của payOS) -> IGNORED_EMPTY, không tính là lỗi")
    void unknownOrderOutsideRange_IgnoredEmpty() {
        when(paymentRepository.findByOrderCodeForUpdate(123L)).thenReturn(Optional.empty());

        WebhookOutcome outcome = process(webhook(true, "00"), data(123L, AMOUNT, "00", "link-x"));

        assertThat(outcome.result()).isEqualTo(WebhookResult.IGNORED_EMPTY);
        assertThat(outcome.result().isFailure()).isFalse();
    }

    @Test
    @DisplayName("outcome: payment_link_id không khớp link đã lưu -> LINK_MISMATCH")
    void linkMismatch() {
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));

        WebhookOutcome outcome = process(webhook(true, "00"), data(ORDER_CODE, AMOUNT, "00", "link-KHAC"));

        assertThat(outcome.result()).isEqualTo(WebhookResult.LINK_MISMATCH);
        assertThat(outcome.result().isFailure()).isTrue();
    }

    @Test
    @DisplayName("outcome: đơn chờ + đủ tiền -> PROCESSED, kèm order_code, số tiền, link và mã payOS")
    void paid_Processed() {
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));

        WebhookOutcome outcome = process(webhook(true, "00"), data(ORDER_CODE, AMOUNT, "00", "link-1"));

        assertThat(outcome.result()).isEqualTo(WebhookResult.PROCESSED);
        assertThat(outcome.orderCode()).isEqualTo(ORDER_CODE);
        assertThat(outcome.amount()).isEqualTo(AMOUNT);
        assertThat(outcome.paymentLinkId()).isEqualTo("link-1");
        assertThat(outcome.payosCode()).isEqualTo("00");
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.result().isFailure()).isFalse();
    }

    @Test
    @DisplayName("outcome: đơn đã PAID (payOS gửi lại) -> ALREADY_PAID, không phải lỗi")
    void replay_AlreadyPaid() {
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(payment(PaymentStatus.PAID)));

        WebhookOutcome outcome = process(webhook(true, "00"), data(ORDER_CODE, AMOUNT, "00", "link-1"));

        assertThat(outcome.result()).isEqualTo(WebhookResult.ALREADY_PAID);
        assertThat(outcome.result().isFailure()).isFalse();
    }

    @ParameterizedTest(name = "đơn {0} nhận tiền -> ORDER_CLOSED")
    @DisplayName("outcome: đơn đã đóng mà vẫn nhận tiền -> ORDER_CLOSED (lỗi, bất biến bị vỡ)")
    @EnumSource(value = PaymentStatus.class, names = {"CANCELLED", "EXPIRED"})
    void closedOrder_OrderClosed(PaymentStatus status) {
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(payment(status)));

        WebhookOutcome outcome = process(webhook(true, "00"), data(ORDER_CODE, AMOUNT, "00", "link-1"));

        assertThat(outcome.result()).isEqualTo(WebhookResult.ORDER_CLOSED);
        assertThat(outcome.result().isFailure()).isTrue();
    }

    @Test
    @DisplayName("outcome: số tiền thu nhỏ hơn số đơn -> UNDERPAID (lỗi, không giao hàng)")
    void underpaid() {
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));

        WebhookOutcome outcome = process(webhook(true, "00"), data(ORDER_CODE, AMOUNT - 1, "00", "link-1"));

        assertThat(outcome.result()).isEqualTo(WebhookResult.UNDERPAID);
        assertThat(outcome.result().isFailure()).isTrue();
    }

    @Test
    @DisplayName("WebhookResult.isFailure: đúng 7 loại lỗi theo plan, 4 loại còn lại không phải lỗi")
    void failureClassification() {
        assertThat(java.util.Arrays.stream(WebhookResult.values()).filter(WebhookResult::isFailure))
                .containsExactlyInAnyOrder(WebhookResult.INVALID_BODY, WebhookResult.INVALID_SIGNATURE,
                        WebhookResult.ORDER_NOT_FOUND, WebhookResult.LINK_MISMATCH, WebhookResult.ORDER_CLOSED,
                        WebhookResult.UNDERPAID, WebhookResult.ERROR);
    }
}
