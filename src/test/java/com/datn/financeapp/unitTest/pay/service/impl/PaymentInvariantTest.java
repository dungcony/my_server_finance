package com.datn.financeapp.unitTest.pay.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.pay.config.PayOsProperties;
import com.datn.financeapp.module.pay.dto.req.PaymentCreateReq;
import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.events.publisher.PaymentPaidEvent;
import com.datn.financeapp.module.pay.exceptions.PaymentException;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;
import com.datn.financeapp.module.pay.mapper.PaymentMapper;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

@ExtendWith(MockitoExtension.class)
class PaymentInvariantTest {

    private static final long ORDER_CODE = 100_001L;
    private static final long AMOUNT = 49_000L;
    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PayOsClientHelper payOsClientHelper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final PaymentMapper paymentMapper = new PaymentMapperImpl();

    private PaymentServiceImpl paymentService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PayOsProperties properties = new PayOsProperties(
                true, "client", "key", "checksum", null, "http://return", "http://cancel", 15);
        paymentService = new PaymentServiceImpl(
                paymentRepository, payOsClientHelper, properties, paymentMapper, eventPublisher, clock);
        userId = UUID.randomUUID();
    }

    private Payment pendingPayment() {
        return Payment.builder()
                .id(UUID.randomUUID())
                .orderCode(ORDER_CODE)
                .userId(userId)
                .type(PaymentType.PREMIUM_UPGRADE)
                .referenceId("PREMIUM_1M")
                .amount(AMOUNT)
                .status(PaymentStatus.PENDING)
                .payosLinkId("link-old")
                .expiresAt(NOW.plusSeconds(600))
                .build();
    }

    private PaymentLink mockLink(PaymentLinkStatus status, Long amountPaid) {
        PaymentLink link = mock(PaymentLink.class);
        lenient().when(link.getStatus()).thenReturn(status);
        lenient().when(link.getAmountPaid()).thenReturn(amountPaid);
        lenient().when(link.getAmount()).thenReturn(amountPaid);
        return link;
    }

    @Test
    @DisplayName("Bất biến #13: received >= amount khi closeUnpaid -> chuyển PAID và phát event, không bao giờ CANCELLED")
    void invariant_ReceivedFull_BecomesPaid() {
        Payment pending = pendingPayment();
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenReturn(mockLink(PaymentLinkStatus.PENDING, AMOUNT));

        paymentService.cancel(userId, ORDER_CODE);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pending.getAmountPaid()).isEqualTo(AMOUNT);
        verify(eventPublisher).publishEvent(any(PaymentPaidEvent.class));
        verify(payOsClientHelper, never()).cancelLink(anyLong());
    }

    @Test
    @DisplayName("Bất biến #13: 0 < received < amount khi closeUnpaid -> giữ PENDING, ném lỗi, không đóng đơn")
    void invariant_ReceivedPartial_StaysPendingThrows() {
        Payment pending = pendingPayment();
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenReturn(mockLink(PaymentLinkStatus.PENDING, 20_000L));

        assertThatThrownBy(() -> paymentService.cancel(userId, ORDER_CODE))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo(ErrorCode.PAYMENT_PROVIDER_ERROR);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(eventPublisher);
        verify(payOsClientHelper, never()).cancelLink(anyLong());
    }

    @Test
    @DisplayName("Bất biến #13: received == 0 và link đã dead (CANCELLED) -> ghi CANCELLED")
    void invariant_ReceivedZero_DeadLink_Cancels() {
        Payment pending = pendingPayment();
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenReturn(mockLink(PaymentLinkStatus.CANCELLED, 0L));

        paymentService.cancel(userId, ORDER_CODE);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verifyNoInteractions(eventPublisher);
        verify(payOsClientHelper, never()).cancelLink(anyLong());
    }

    @Test
    @DisplayName("Bất biến #13: đơn đã CANCELLED nhận webhook thanh toán -> đơn không đổi, không phát event")
    void invariant_ClosedOrder_WebhookIgnored() {
        Payment cancelled = pendingPayment();
        cancelled.setStatus(PaymentStatus.CANCELLED);

        Webhook webhook = mock(Webhook.class);
        lenient().when(webhook.getSuccess()).thenReturn(true);
        lenient().when(webhook.getCode()).thenReturn("00");

        WebhookData data = mock(WebhookData.class);
        lenient().when(data.getOrderCode()).thenReturn(ORDER_CODE);
        lenient().when(data.getAmount()).thenReturn(AMOUNT);
        lenient().when(data.getCode()).thenReturn("00");
        lenient().when(data.getPaymentLinkId()).thenReturn("link-old");

        when(payOsClientHelper.verifyWebhook(webhook)).thenReturn(data);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(cancelled));

        paymentService.processWebhook(webhook);

        assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(cancelled.getAmountPaid()).isNull();
        verifyNoInteractions(eventPublisher);
    }
}
