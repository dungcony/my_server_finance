package com.datn.financeapp.unitTest.pay.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.pay.config.PayOsProperties;
import com.datn.financeapp.module.pay.config.PaymentTopupProperties;
import com.datn.financeapp.module.pay.dto.req.PaymentCreateReq;
import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.pay.dto.res.PaymentStatusRes;
import com.datn.financeapp.module.pay.dto.res.WebhookOutcome;
import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import com.datn.financeapp.module.pay.events.publisher.PaymentForfeitedEvent;
import com.datn.financeapp.module.pay.events.publisher.PaymentPaidEvent;
import com.datn.financeapp.module.pay.events.publisher.PaymentPartiallyPaidEvent;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;
import com.datn.financeapp.module.pay.mapper.PaymentMapper;
import com.datn.financeapp.module.pay.mapper.PaymentMapperImpl;
import com.datn.financeapp.module.pay.repository.PaymentRepository;
import com.datn.financeapp.module.pay.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplPartialTest {

    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final long ORDER_CODE = 200003L;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PayOsClientHelper payOsClientHelper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentMapper paymentMapper;
    private PayOsProperties properties;
    private PaymentTopupProperties topupProperties;
    private Clock clock;
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        paymentMapper = new PaymentMapperImpl();
        properties = new PayOsProperties(
                true, "client", "key", "checksum", null,
                "http://localhost/return", "http://localhost/cancel", 15);
        topupProperties = new PaymentTopupProperties(86400, 3600, 60);

        paymentService = new PaymentServiceImpl(
                paymentRepository,
                payOsClientHelper,
                properties,
                topupProperties,
                paymentMapper,
                eventPublisher,
                clock
        );
    }

    @Test
    @DisplayName("getStatus: khi phát hiện khách chuyển thiếu tiền -> chuyển sang PARTIALLY_PAID và phát sự kiện")
    void getStatus_WhenUnderpaid_TransitionsToPartiallyPaid() {
        Payment payment = buildPayment(PaymentStatus.PENDING, 4000L);
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, USER_ID)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(payment));

        PaymentLink linkMock = mock(PaymentLink.class);
        when(linkMock.getAmountPaid()).thenReturn(3000L);
        when(linkMock.getStatus()).thenReturn(PaymentLinkStatus.PENDING);
        when(payOsClientHelper.getLink(ORDER_CODE)).thenReturn(linkMock);

        PaymentStatusRes res = paymentService.getStatus(USER_ID, ORDER_CODE);

        assertThat(res.status()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(res.amountPaid()).isEqualTo(3000L);
        assertThat(res.amountRemaining()).isEqualTo(1000L);
        assertThat(res.topupDeadline()).isEqualTo(NOW.plusSeconds(86400));

        verify(eventPublisher).publishEvent(any(PaymentPartiallyPaidEvent.class));
    }

    @Test
    @DisplayName("getStatus: khi đơn PARTIALLY_PAID quá hạn bù tiền -> chuyển sang FORFEITED và phạt giữ tiền")
    void getStatus_WhenPartiallyPaidOverdue_TransitionsToForfeited() {
        Payment payment = buildPayment(PaymentStatus.PARTIALLY_PAID, 4000L);
        payment.setAmountPaid(3000L);
        payment.setTopupDeadline(NOW.minusSeconds(10)); // đã quá hạn bù

        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, USER_ID)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(payment));

        PaymentStatusRes res = paymentService.getStatus(USER_ID, ORDER_CODE);

        assertThat(res.status()).isEqualTo(PaymentStatus.FORFEITED);
        assertThat(payment.getForfeitedAt()).isEqualTo(NOW);
        verify(payOsClientHelper).cancelLink(ORDER_CODE);
        verify(eventPublisher).publishEvent(any(PaymentForfeitedEvent.class));
    }

    @Test
    @DisplayName("cancel: khi đơn đang PARTIALLY_PAID -> từ chối huỷ và ném lỗi PAYMENT_CANNOT_CANCEL_PARTIAL")
    void cancel_WhenPartiallyPaid_ThrowsError() {
        Payment payment = buildPayment(PaymentStatus.PARTIALLY_PAID, 4000L);
        payment.setAmountPaid(3000L);

        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, USER_ID)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancel(USER_ID, ORDER_CODE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Đơn thanh toán đã nhận tiền, không thể huỷ");

        verify(payOsClientHelper, never()).cancelLink(anyLong());
    }

    @Test
    @DisplayName("createPayment: khi người dùng có đơn PARTIALLY_PAID còn trong hạn bù -> chặn tạo đơn mới")
    void createPayment_WhenHasActivePartiallyPaid_ThrowsConflict() {
        Payment active = buildPayment(PaymentStatus.PARTIALLY_PAID, 4000L);
        active.setAmountPaid(3000L);
        active.setTopupDeadline(NOW.plusSeconds(3600)); // còn hạn

        when(paymentRepository.findActiveForUpdateNoWait(eq(USER_ID), eq(PaymentType.PREMIUM_UPGRADE), any()))
                .thenReturn(Optional.of(active));

        PaymentCreateReq req = new PaymentCreateReq(USER_ID, PaymentType.PREMIUM_UPGRADE, "PREMIUM_1M", 49000L);

        assertThatThrownBy(() -> paymentService.createPayment(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Đơn thanh toán đang chờ nạp nốt số tiền còn thiếu");
    }

    @Test
    @DisplayName("createPayment: khi đơn PARTIALLY_PAID đã quá hạn bù -> phạt đơn cũ và cho phép tạo đơn mới")
    void createPayment_WhenHasOverduePartiallyPaid_ForfeitsAndCreatesNew() {
        Payment active = buildPayment(PaymentStatus.PARTIALLY_PAID, 4000L);
        active.setAmountPaid(3000L);
        active.setTopupDeadline(NOW.minusSeconds(10)); // quá hạn

        when(paymentRepository.findActiveForUpdateNoWait(eq(USER_ID), eq(PaymentType.PREMIUM_UPGRADE), any()))
                .thenReturn(Optional.of(active));
        when(paymentRepository.nextOrderCode()).thenReturn(200004L);

        CreatePaymentLinkResponse linkRes = mock(CreatePaymentLinkResponse.class);
        when(linkRes.getCheckoutUrl()).thenReturn("http://pay.payos.vn/web/test-link-2");
        when(linkRes.getPaymentLinkId()).thenReturn("link-id-2");
        when(payOsClientHelper.createLink(eq(200004L), eq(49000L), any(), anyLong())).thenReturn(linkRes);

        PaymentCreateReq req = new PaymentCreateReq(USER_ID, PaymentType.PREMIUM_UPGRADE, "PREMIUM_1M", 49000L);
        PaymentCreateRes res = paymentService.createPayment(req);

        assertThat(res.orderCode()).isEqualTo(200004L);
        assertThat(active.getStatus()).isEqualTo(PaymentStatus.FORFEITED);
        verify(eventPublisher).publishEvent(any(PaymentForfeitedEvent.class));
    }

    @Test
    @DisplayName("processWebhook: khi nhận được số tiền thiếu -> chuyển trạng thái PARTIALLY_PAID")
    void processWebhook_WhenUnderpaid_TransitionsToPartiallyPaid() {
        Payment payment = buildPayment(PaymentStatus.PENDING, 4000L);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(payment));

        Webhook webhook = mock(Webhook.class);
        when(webhook.getSuccess()).thenReturn(true);
        when(webhook.getCode()).thenReturn("00");

        WebhookData data = mock(WebhookData.class);
        when(data.getOrderCode()).thenReturn(ORDER_CODE);
        when(data.getAmount()).thenReturn(3000L);
        when(data.getCode()).thenReturn("00");
        when(data.getPaymentLinkId()).thenReturn("link-id-1");

        when(payOsClientHelper.verifyWebhook(webhook)).thenReturn(data);

        WebhookOutcome outcome = paymentService.processWebhook(webhook);

        assertThat(outcome.result()).isEqualTo(WebhookResult.UNDERPAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(payment.getAmountPaid()).isEqualTo(3000L);
        verify(eventPublisher).publishEvent(any(PaymentPartiallyPaidEvent.class));
        verify(eventPublisher, never()).publishEvent(any(PaymentPaidEvent.class));
    }

    @Test
    @DisplayName("processWebhook: đơn đang PARTIALLY_PAID nhận đủ tiền bù -> chuyển PAID và kích hoạt Premium")
    void processWebhook_WhenPartiallyPaidReceivesRemaining_TransitionsToPaid() {
        Payment payment = buildPayment(PaymentStatus.PARTIALLY_PAID, 4000L);
        payment.setAmountPaid(3000L);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(payment));

        Webhook webhook = mock(Webhook.class);
        when(webhook.getSuccess()).thenReturn(true);
        when(webhook.getCode()).thenReturn("00");

        WebhookData data = mock(WebhookData.class);
        when(data.getOrderCode()).thenReturn(ORDER_CODE);
        when(data.getAmount()).thenReturn(4000L); // payOS báo tổng đã thu 4000
        when(data.getCode()).thenReturn("00");
        when(data.getPaymentLinkId()).thenReturn("link-id-1");

        when(payOsClientHelper.verifyWebhook(webhook)).thenReturn(data);

        WebhookOutcome outcome = paymentService.processWebhook(webhook);

        assertThat(outcome.result()).isEqualTo(WebhookResult.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmountPaid()).isEqualTo(4000L);
        verify(eventPublisher).publishEvent(any(PaymentPaidEvent.class));
    }

    private Payment buildPayment(PaymentStatus status, long amount) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .orderCode(ORDER_CODE)
                .userId(USER_ID)
                .type(PaymentType.PREMIUM_UPGRADE)
                .referenceId("PREMIUM_1M")
                .amount(amount)
                .status(status)
                .payosLinkId("link-id-1")
                .expiresAt(NOW.plusSeconds(900))
                .build();
    }
}
