package com.datn.financeapp.unitTest.pay.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.pay.config.PayOsProperties;
import com.datn.financeapp.module.pay.config.PaymentTopupProperties;
import com.datn.financeapp.module.pay.dto.req.PaymentCreateReq;
import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.pay.dto.res.PaymentListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentStatusRes;
import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.events.publisher.PaymentPaidEvent;
import com.datn.financeapp.module.pay.exceptions.PaymentException;
import com.datn.financeapp.module.pay.exceptions.PaymentNotFoundException;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;
import com.datn.financeapp.module.pay.mapper.PaymentMapper;
import com.datn.financeapp.module.pay.mapper.PaymentMapperImpl;
import com.datn.financeapp.module.pay.repository.PaymentRepository;
import com.datn.financeapp.module.pay.service.impl.PaymentServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

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
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PayOsProperties properties = new PayOsProperties(
                true, "client", "key", "checksum", null, "http://return", "http://cancel", 15);
        PaymentTopupProperties topupProperties = new PaymentTopupProperties(86400, 3600, 60);
        paymentService = new PaymentServiceImpl(
                paymentRepository, payOsClientHelper, properties, topupProperties, paymentMapper, eventPublisher, clock);
        // createNewPayment đăng ký bước huỷ link bù trừ khi rollback; unit test không có transaction nên bật đồng bộ hoá tay
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    private Payment payment(PaymentStatus status, Instant expiresAt) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .orderCode(ORDER_CODE)
                .userId(userId)
                .type(PaymentType.PREMIUM_UPGRADE)
                .referenceId("PREMIUM_1M")
                .amount(AMOUNT)
                .status(status)
                .payosLinkId("link-old")
                .expiresAt(expiresAt)
                .build();
    }

    private PaymentCreateReq createReq(String referenceId) {
        return new PaymentCreateReq(userId, PaymentType.PREMIUM_UPGRADE, referenceId, AMOUNT);
    }

    private PaymentLink link(PaymentLinkStatus status, Long amount) {
        PaymentLink link = mock(PaymentLink.class);
        lenient().when(link.getStatus()).thenReturn(status);
        lenient().when(link.getAmount()).thenReturn(amount);
        lenient().when(link.getAmountPaid()).thenReturn(amount);
        return link;
    }

    private void stubNoPending() {
        when(paymentRepository.findPendingForUpdateNoWait(userId, PaymentType.PREMIUM_UPGRADE, PaymentStatus.PENDING))
                .thenReturn(Optional.empty());
    }

    private void stubPending(Payment pending) {
        when(paymentRepository.findPendingForUpdateNoWait(userId, PaymentType.PREMIUM_UPGRADE, PaymentStatus.PENDING))
                .thenReturn(Optional.of(pending));
    }

    private void stubCreateLink(long orderCode) {
        CreatePaymentLinkResponse response = mock(CreatePaymentLinkResponse.class);
        lenient().when(response.getPaymentLinkId()).thenReturn("link-new");
        lenient().when(response.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/link-new");
        when(paymentRepository.nextOrderCode()).thenReturn(orderCode);
        when(payOsClientHelper.createLink(eq(orderCode), eq(AMOUNT), any(), anyLong())).thenReturn(response);
    }

    private Webhook webhook(boolean success, String code) {
        Webhook webhook = mock(Webhook.class);
        lenient().when(webhook.getSuccess()).thenReturn(success);
        lenient().when(webhook.getCode()).thenReturn(code);
        return webhook;
    }

    private WebhookData webhookData(Long orderCode, Long amount, String code) {
        return webhookData(orderCode, amount, code, "link-old");
    }

    private WebhookData webhookData(Long orderCode, Long amount, String code, String paymentLinkId) {
        WebhookData data = mock(WebhookData.class);
        lenient().when(data.getOrderCode()).thenReturn(orderCode);
        lenient().when(data.getCode()).thenReturn(code);
        lenient().when(data.getAmount()).thenReturn(amount);
        lenient().when(data.getPaymentLinkId()).thenReturn(paymentLinkId);
        return data;
    }

    @Test
    @DisplayName("createPayment: chưa có đơn chờ -> lưu đơn PENDING, gọi payOS đúng số tiền, trả checkout_url")
    void createPayment_NoPending_CreatesLink() {
        stubNoPending();
        stubCreateLink(ORDER_CODE);

        PaymentCreateRes res = paymentService.createPayment(createReq("PREMIUM_1M"));

        assertThat(res.orderCode()).isEqualTo(ORDER_CODE);
        assertThat(res.referenceId()).isEqualTo("PREMIUM_1M");
        assertThat(res.amount()).isEqualTo(AMOUNT);
        assertThat(res.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(res.checkoutUrl()).isEqualTo("https://pay.payos.vn/web/link-new");
        assertThat(res.expiresAt()).isAfter(NOW);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getType()).isEqualTo(PaymentType.PREMIUM_UPGRADE);
        assertThat(saved.getValue().getPayosLinkId()).isEqualTo("link-new");
    }

    @Test
    @DisplayName("createPayment: còn đơn chờ chưa hết hạn -> huỷ link cũ (ghi CANCELLED), tạo đơn mới + link mới")
    void createPayment_PendingNotExpired_CancelsOldAndCreatesNew() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        stubPending(pending);
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PENDING, null));
        when(payOsClientHelper.cancelLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.CANCELLED, null));
        stubCreateLink(ORDER_CODE + 1);

        PaymentCreateRes res = paymentService.createPayment(createReq("PREMIUM_6M"));

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(payOsClientHelper).cancelLink(ORDER_CODE);
        verify(paymentRepository).flush();
        assertThat(res.orderCode()).isEqualTo(ORDER_CODE + 1);
        assertThat(res.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("createPayment: đơn chờ quá hạn -> huỷ link payOS, ghi EXPIRED, rồi tạo đơn mới")
    void createPayment_PendingOverdue_CancelsThenCreatesNew() {
        Payment overdue = payment(PaymentStatus.PENDING, NOW.minusSeconds(60));
        stubPending(overdue);
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PENDING, null));
        when(payOsClientHelper.cancelLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.CANCELLED, null));
        stubCreateLink(ORDER_CODE + 1);

        PaymentCreateRes res = paymentService.createPayment(createReq("PREMIUM_1M"));

        assertThat(overdue.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(payOsClientHelper).cancelLink(ORDER_CODE);
        verify(paymentRepository).flush();
        assertThat(res.orderCode()).isEqualTo(ORDER_CODE + 1);
        assertThat(res.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("createPayment: huỷ link payOS lỗi -> ném lỗi, đơn cũ giữ PENDING, không tạo đơn mới")
    void createPayment_OverdueCancelFails_NoNewOrder() {
        Payment overdue = payment(PaymentStatus.PENDING, NOW.minusSeconds(60));
        stubPending(overdue);
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PENDING, null));
        when(payOsClientHelper.cancelLink(ORDER_CODE)).thenThrow(new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR));

        assertThatThrownBy(() -> paymentService.createPayment(createReq("PREMIUM_1M")))
                .isInstanceOf(PaymentException.class);

        assertThat(overdue.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(payOsClientHelper, never()).createLink(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("createPayment: đơn chờ nhưng payOS báo đã trả -> ghi PAID, phát event, trả đơn PAID, không tạo đơn mới")
    void createPayment_PendingPaidAtPayOs_ReturnsPaidOrder() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        stubPending(pending);
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PAID, AMOUNT));

        PaymentCreateRes res = paymentService.createPayment(createReq("PREMIUM_1M"));

        assertThat(res.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pending.getAmountPaid()).isEqualTo(AMOUNT);
        verify(eventPublisher).publishEvent(any(PaymentPaidEvent.class));
        verify(payOsClientHelper, never()).cancelLink(anyLong());
        verify(payOsClientHelper, never()).createLink(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("createPayment: bị khoá dòng (PessimisticLockingFailureException) -> dịch sang 409 REQUEST_IN_PROGRESS")
    void createPayment_LockContention_ThrowsRequestInProgress() {
        when(paymentRepository.findPendingForUpdateNoWait(userId, PaymentType.PREMIUM_UPGRADE, PaymentStatus.PENDING))
                .thenThrow(new CannotAcquireLockException("lock busy"));

        assertThatThrownBy(() -> paymentService.createPayment(createReq("PREMIUM_1M")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.REQUEST_IN_PROGRESS.getCode());

        verifyNoInteractions(payOsClientHelper);
    }

    // ----------------------------- cancel -----------------------------

    @Test
    @DisplayName("cancel: đơn PENDING của mình -> huỷ link payOS, ghi CANCELLED và trả kết quả")
    void cancel_Pending_CancelsAndReturnsCancelled() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PENDING, null));
        when(payOsClientHelper.cancelLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.CANCELLED, null));

        PaymentStatusRes res = paymentService.cancel(userId, ORDER_CODE);

        assertThat(res.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(payOsClientHelper).cancelLink(ORDER_CODE);
    }

    @Test
    @DisplayName("cancel: không phải đơn của mình hoặc không tồn tại -> 404 PaymentNotFoundException")
    void cancel_OtherUserOrder_NotFound() {
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(false);

        assertThatThrownBy(() -> paymentService.cancel(userId, ORDER_CODE))
                .isInstanceOf(PaymentNotFoundException.class);

        verify(paymentRepository, never()).findByOrderCodeForUpdate(anyLong());
        verifyNoInteractions(payOsClientHelper);
    }

    @Test
    @DisplayName("cancel: đơn đã PAID -> trả nguyên trạng thái PAID, không gọi cancelLink")
    void cancel_AlreadyPaid_ReturnsPaidWithoutCancelling() {
        Payment paid = payment(PaymentStatus.PAID, NOW.plusSeconds(600));
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(paid));

        PaymentStatusRes res = paymentService.cancel(userId, ORDER_CODE);

        assertThat(res.status()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(payOsClientHelper);
    }

    @Test
    @DisplayName("cancel: payOS báo đã trả lúc bấm huỷ -> ghi PAID, phát event, không huỷ link")
    void cancel_PayOsReportsPaid_MarksPaid() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PAID, AMOUNT));

        PaymentStatusRes res = paymentService.cancel(userId, ORDER_CODE);

        assertThat(res.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(eventPublisher).publishEvent(any(PaymentPaidEvent.class));
        verify(payOsClientHelper, never()).cancelLink(anyLong());
    }

    // ----------------------------- processWebhook -----------------------------

    @Test
    @DisplayName("processWebhook: hợp lệ -> ghi PAID, lưu amountPaid, phát PaymentPaidEvent đúng một lần")
    void processWebhook_Valid_MarksPaidAndPublishesEvent() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        Webhook webhook = webhook(true, "00");
        when(payOsClientHelper.verifyWebhook(webhook)).thenAnswer(inv -> webhookData(ORDER_CODE, AMOUNT, "00", "link-old"));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));

        paymentService.processWebhook(webhook);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pending.getPaidAt()).isEqualTo(NOW);
        assertThat(pending.getAmountPaid()).isEqualTo(AMOUNT);
        ArgumentCaptor<PaymentPaidEvent> event = ArgumentCaptor.forClass(PaymentPaidEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(userId);
        assertThat(event.getValue().type()).isEqualTo(PaymentType.PREMIUM_UPGRADE);
        assertThat(event.getValue().referenceId()).isEqualTo("PREMIUM_1M");
        assertThat(event.getValue().orderCode()).isEqualTo(ORDER_CODE);
    }

    @Test
    @DisplayName("processWebhook: trả dư tiền (#20) -> ghi PAID, lưu đúng số thực nhận, phát event")
    void processWebhook_ExcessAmount_MarksPaidWithExcess() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        Webhook webhook = webhook(true, "00");
        long paidExcess = AMOUNT + 5_000L;
        when(payOsClientHelper.verifyWebhook(webhook)).thenAnswer(inv -> webhookData(ORDER_CODE, paidExcess, "00", "link-old"));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));

        paymentService.processWebhook(webhook);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pending.getAmountPaid()).isEqualTo(paidExcess);
        verify(eventPublisher).publishEvent(any(PaymentPaidEvent.class));
    }

    @Test
    @DisplayName("processWebhook: payOS gửi lại khi đơn đã PAID -> bỏ qua, không phát event lần hai, không sửa amountPaid")
    void processWebhook_AlreadyPaid_Idempotent() {
        Payment paid = payment(PaymentStatus.PAID, NOW.plusSeconds(600));
        paid.setAmountPaid(AMOUNT);
        Webhook webhook = webhook(true, "00");
        when(payOsClientHelper.verifyWebhook(webhook)).thenAnswer(inv -> webhookData(ORDER_CODE, AMOUNT + 1000L, "00", "link-old"));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(paid));

        paymentService.processWebhook(webhook);

        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getAmountPaid()).isEqualTo(AMOUNT);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("processWebhook: nhận tiền cho đơn đã CANCELLED/EXPIRED -> không giao hàng, không đổi trạng thái")
    void processWebhook_ClosedOrder_IgnoredAndAlarms() {
        Payment cancelled = payment(PaymentStatus.CANCELLED, NOW.plusSeconds(600));
        Webhook webhook = webhook(true, "00");
        when(payOsClientHelper.verifyWebhook(webhook)).thenAnswer(inv -> webhookData(ORDER_CODE, AMOUNT, "00", "link-old"));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(cancelled));

        paymentService.processWebhook(webhook);

        assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(cancelled.getAmountPaid()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("processWebhook: paymentLinkId không khớp với link đã lưu -> bỏ qua")
    void processWebhook_MismatchedLinkId_Ignored() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        Webhook webhook = webhook(true, "00");
        when(payOsClientHelper.verifyWebhook(webhook)).thenAnswer(inv -> webhookData(ORDER_CODE, AMOUNT, "00", "different-link-id"));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));

        paymentService.processWebhook(webhook);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(pending.getAmountPaid()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("processWebhook: thiếu tiền -> đơn vẫn PENDING, không phát event")
    void processWebhook_AmountUnderpaid_StaysPending() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        Webhook webhook = webhook(true, "00");
        when(payOsClientHelper.verifyWebhook(webhook)).thenAnswer(inv -> webhookData(ORDER_CODE, AMOUNT - 1, "00", "link-old"));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));

        paymentService.processWebhook(webhook);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(pending.getAmountPaid()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("processWebhook: không tìm thấy đơn -> không ném lỗi, không phát event")
    void processWebhook_OrderNotFound_Ignored() {
        Webhook webhook = webhook(true, "00");
        when(payOsClientHelper.verifyWebhook(webhook)).thenAnswer(inv -> webhookData(ORDER_CODE, AMOUNT, "00", "link-old"));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.empty());

        paymentService.processWebhook(webhook);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("processWebhook: webhook báo không thành công -> bỏ qua, không khoá đơn")
    void processWebhook_NotSuccessful_Ignored() {
        Webhook webhook = webhook(false, null);
        when(payOsClientHelper.verifyWebhook(webhook)).thenAnswer(inv -> webhookData(ORDER_CODE, AMOUNT, "01", "link-old"));

        paymentService.processWebhook(webhook);

        verify(paymentRepository, never()).findByOrderCodeForUpdate(anyLong());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("processWebhook: sai chữ ký -> lỗi được ném ra để trả 400")
    void processWebhook_InvalidSignature_Throws() {
        Webhook webhook = mock(Webhook.class);
        when(payOsClientHelper.verifyWebhook(webhook))
                .thenThrow(new PaymentException(ErrorCode.PAYMENT_WEBHOOK_INVALID));

        assertThatThrownBy(() -> paymentService.processWebhook(webhook))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo("PAYMENT_WEBHOOK_INVALID");
        verifyNoInteractions(eventPublisher);
    }

    // ----------------------------- getStatus -----------------------------

    @Test
    @DisplayName("getStatus: đơn của user khác -> PaymentNotFoundException (không lộ việc đơn tồn tại)")
    void getStatus_OtherUsersOrder_NotFound() {
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(false);

        assertThatThrownBy(() -> paymentService.getStatus(userId, ORDER_CODE))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("getStatus: đơn đã PAID -> trả luôn, không hỏi payOS")
    void getStatus_Paid_NoProviderCall() {
        Payment paid = payment(PaymentStatus.PAID, NOW.plusSeconds(600));
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(paid));

        PaymentStatusRes res = paymentService.getStatus(userId, ORDER_CODE);

        assertThat(res.status()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(payOsClientHelper);
    }

    @Test
    @DisplayName("getStatus: đơn chờ, payOS báo đã trả -> ghi PAID và phát event")
    void getStatus_PendingButPaidAtPayOs_MarksPaid() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PAID, AMOUNT));

        PaymentStatusRes res = paymentService.getStatus(userId, ORDER_CODE);

        assertThat(res.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pending.getAmountPaid()).isEqualTo(AMOUNT);
        verify(eventPublisher).publishEvent(any(PaymentPaidEvent.class));
    }

    @Test
    @DisplayName("getStatus: payOS báo link CANCELLED -> ghi CANCELLED")
    void getStatus_PayOsReportsCancelled_MarksCancelled() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.CANCELLED, null));

        PaymentStatusRes res = paymentService.getStatus(userId, ORDER_CODE);

        assertThat(res.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("getStatus: payOS lỗi khi polling -> không ném lỗi, vẫn trả PENDING")
    void getStatus_ProviderError_KeepsPending() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenThrow(new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR));

        PaymentStatusRes res = paymentService.getStatus(userId, ORDER_CODE);

        assertThat(res.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("getStatus: đơn chờ đã quá hạn -> huỷ link, trả EXPIRED")
    void getStatus_PendingOverdue_ReturnsExpired() {
        Payment overdue = payment(PaymentStatus.PENDING, NOW.minusSeconds(60));
        when(paymentRepository.existsByOrderCodeAndUserId(ORDER_CODE, userId)).thenReturn(true);
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(overdue));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PENDING, null));
        when(payOsClientHelper.cancelLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.CANCELLED, null));

        PaymentStatusRes res = paymentService.getStatus(userId, ORDER_CODE);

        assertThat(res.status()).isEqualTo(PaymentStatus.EXPIRED);
        verify(payOsClientHelper).cancelLink(ORDER_CODE);
    }

    // ----------------------------- expireIfOverdue -----------------------------

    @Test
    @DisplayName("expireIfOverdue: đơn chưa tới hạn -> không đụng payOS")
    void expireIfOverdue_NotYetDue_Skipped() {
        Payment pending = payment(PaymentStatus.PENDING, NOW.plusSeconds(600));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(pending));

        paymentService.expireIfOverdue(ORDER_CODE);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(payOsClientHelper);
    }

    @Test
    @DisplayName("expireIfOverdue: đơn đã PAID (webhook xử lý trước) -> bỏ qua")
    void expireIfOverdue_AlreadyPaid_Skipped() {
        Payment paid = payment(PaymentStatus.PAID, NOW.minusSeconds(60));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(paid));

        paymentService.expireIfOverdue(ORDER_CODE);

        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(payOsClientHelper);
    }

    @Test
    @DisplayName("expireIfOverdue: đơn quá hạn -> huỷ link payOS rồi ghi EXPIRED")
    void expireIfOverdue_Overdue_Expires() {
        Payment overdue = payment(PaymentStatus.PENDING, NOW.minusSeconds(60));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(overdue));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PENDING, null));
        when(payOsClientHelper.cancelLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.CANCELLED, null));

        paymentService.expireIfOverdue(ORDER_CODE);

        assertThat(overdue.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(payOsClientHelper).cancelLink(ORDER_CODE);
    }

    @Test
    @DisplayName("expireIfOverdue: link payOS đã tự hết hạn -> ghi EXPIRED, không gọi huỷ lại")
    void expireIfOverdue_LinkAlreadyExpiredAtPayOs_NoCancelCall() {
        Payment overdue = payment(PaymentStatus.PENDING, NOW.minusSeconds(60));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(overdue));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.EXPIRED, null));

        paymentService.expireIfOverdue(ORDER_CODE);

        assertThat(overdue.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(payOsClientHelper, never()).cancelLink(anyLong());
    }

    @Test
    @DisplayName("expireIfOverdue: huỷ link lỗi -> ném lỗi, đơn giữ PENDING để lần sau thử lại")
    void expireIfOverdue_CancelFails_StaysPending() {
        Payment overdue = payment(PaymentStatus.PENDING, NOW.minusSeconds(60));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(overdue));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PENDING, null));
        when(payOsClientHelper.cancelLink(ORDER_CODE)).thenThrow(new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR));

        assertThatThrownBy(() -> paymentService.expireIfOverdue(ORDER_CODE)).isInstanceOf(PaymentException.class);

        assertThat(overdue.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("expireIfOverdue: payOS báo đã trả -> ghi PAID và phát event thay vì EXPIRED")
    void expireIfOverdue_PaidAtPayOs_MarksPaid() {
        Payment overdue = payment(PaymentStatus.PENDING, NOW.minusSeconds(60));
        when(paymentRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(overdue));
        when(payOsClientHelper.getLink(ORDER_CODE)).thenAnswer(inv -> link(PaymentLinkStatus.PAID, AMOUNT));

        paymentService.expireIfOverdue(ORDER_CODE);

        assertThat(overdue.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(overdue.getAmountPaid()).isEqualTo(AMOUNT);
        verify(eventPublisher).publishEvent(any(PaymentPaidEvent.class));
    }

    // ----------------------------- other queries -----------------------------

    @Test
    @DisplayName("listMine: trả items đã map và meta phân trang 1-indexed")
    @SuppressWarnings("unchecked")
    void listMine_MapsItemsAndMeta() {
        Payment paid = payment(PaymentStatus.PAID, NOW.plusSeconds(600));
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(paid), PageRequest.of(0, 20), 1));

        PaymentListRes res = paymentService.listMine(userId, null, null, PageRequest.of(0, 20));

        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).orderCode()).isEqualTo(ORDER_CODE);
        assertThat(res.meta().page()).isEqualTo(1);
        assertThat(res.meta().totalItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("findOverdueOrderCodes: chỉ tìm đơn PENDING, giới hạn theo batch")
    void findOverdueOrderCodes_QueriesPendingWithLimit() {
        Instant now = NOW;
        when(paymentRepository.findOverdueOrderCodes(eq(PaymentStatus.PENDING), eq(now), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));

        List<Long> result = paymentService.findOverdueOrderCodes(now, 50);

        assertThat(result).containsExactly(1L, 2L);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findOverdueOrderCodes(eq(PaymentStatus.PENDING), eq(now), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }
}
