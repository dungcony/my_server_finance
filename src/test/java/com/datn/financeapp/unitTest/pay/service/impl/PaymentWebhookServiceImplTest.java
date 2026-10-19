package com.datn.financeapp.unitTest.pay.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.pay.dto.res.WebhookOutcome;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import com.datn.financeapp.module.pay.exceptions.PaymentException;
import com.datn.financeapp.module.pay.service.PaymentService;
import com.datn.financeapp.module.pay.service.PaymentWebhookLogRecorder;
import com.datn.financeapp.module.pay.service.impl.PaymentWebhookServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.payos.model.webhooks.Webhook;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceImplTest {

    private static final String IP = "203.0.113.7";
    private static final String VALID_BODY = "{\"code\":\"00\",\"desc\":\"success\",\"success\":true,"
            + "\"data\":{\"orderCode\":200001,\"amount\":49000,\"code\":\"00\",\"paymentLinkId\":\"link-1\"},\"signature\":\"sig\"}";

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentWebhookLogRecorder recorder;

    private PaymentWebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new PaymentWebhookServiceImpl(paymentService, recorder);
    }

    private WebhookOutcome outcome(WebhookResult result) {
        return new WebhookOutcome(result, 200_001L, "link-1", 49_000L, "00", true, "msg");
    }

    @Test
    @DisplayName("handle: webhook hợp lệ -> xử lý đúng một lần, ghi kết quả HTTP 200 kèm IP, không ném lỗi")
    void handle_Valid_RecordsOutcome() {
        when(paymentService.processWebhook(any(Webhook.class))).thenReturn(outcome(WebhookResult.PROCESSED));

        assertThatCode(() -> webhookService.handle(VALID_BODY, IP)).doesNotThrowAnyException();

        ArgumentCaptor<WebhookOutcome> recorded = ArgumentCaptor.forClass(WebhookOutcome.class);
        ArgumentCaptor<Long> duration = ArgumentCaptor.forClass(Long.class);
        verify(recorder).record(recorded.capture(), eq(200), duration.capture(), eq(IP));
        assertThat(recorded.getValue().result()).isEqualTo(WebhookResult.PROCESSED);
        assertThat(duration.getValue()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("handle: body không phải JSON -> ghi INVALID_BODY (400), ném PAYMENT_WEBHOOK_INVALID, không xử lý nghiệp vụ")
    void handle_MalformedBody_RecordsInvalidBody() {
        assertThatThrownBy(() -> webhookService.handle("{not json", IP))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo("PAYMENT_WEBHOOK_INVALID");

        ArgumentCaptor<WebhookOutcome> recorded = ArgumentCaptor.forClass(WebhookOutcome.class);
        verify(recorder).record(recorded.capture(), eq(400), anyLong(), eq(IP));
        assertThat(recorded.getValue().result()).isEqualTo(WebhookResult.INVALID_BODY);
        verify(paymentService, never()).processWebhook(any());
    }

    @Test
    @DisplayName("handle: sai chữ ký -> ghi INVALID_SIGNATURE (400) kèm order_code/số tiền do bên gửi tự khai, vẫn ném lỗi")
    void handle_InvalidSignature_RecordsAndRethrows() {
        when(paymentService.processWebhook(any(Webhook.class)))
                .thenThrow(new PaymentException(ErrorCode.PAYMENT_WEBHOOK_INVALID));

        assertThatThrownBy(() -> webhookService.handle(VALID_BODY, IP))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo("PAYMENT_WEBHOOK_INVALID");

        ArgumentCaptor<WebhookOutcome> recorded = ArgumentCaptor.forClass(WebhookOutcome.class);
        verify(recorder).record(recorded.capture(), eq(400), anyLong(), eq(IP));
        assertThat(recorded.getValue().result()).isEqualTo(WebhookResult.INVALID_SIGNATURE);
        assertThat(recorded.getValue().orderCode()).isEqualTo(200_001L);
        assertThat(recorded.getValue().amount()).isEqualTo(49_000L);
    }

    @Test
    @DisplayName("handle: payOS chưa cấu hình (lỗi khác sai chữ ký) -> ghi ERROR với HTTP đúng của lỗi, ném lại")
    void handle_NotConfigured_RecordsError() {
        when(paymentService.processWebhook(any(Webhook.class)))
                .thenThrow(new PaymentException(ErrorCode.PAYMENT_NOT_CONFIGURED));

        assertThatThrownBy(() -> webhookService.handle(VALID_BODY, IP)).isInstanceOf(PaymentException.class);

        ArgumentCaptor<WebhookOutcome> recorded = ArgumentCaptor.forClass(WebhookOutcome.class);
        verify(recorder).record(recorded.capture(), eq(503), anyLong(), eq(IP));
        assertThat(recorded.getValue().result()).isEqualTo(WebhookResult.ERROR);
    }

    @Test
    @DisplayName("handle: xử lý nghiệp vụ ném lỗi bất ngờ -> ghi ERROR (500) rồi ném lại đúng lỗi gốc")
    void handle_UnexpectedError_RecordsErrorAndRethrowsSame() {
        IllegalStateException boom = new IllegalStateException("referenceId không hợp lệ");
        when(paymentService.processWebhook(any(Webhook.class))).thenThrow(boom);

        assertThatThrownBy(() -> webhookService.handle(VALID_BODY, IP)).isSameAs(boom);

        ArgumentCaptor<WebhookOutcome> recorded = ArgumentCaptor.forClass(WebhookOutcome.class);
        verify(recorder).record(recorded.capture(), eq(500), anyLong(), eq(IP));
        assertThat(recorded.getValue().result()).isEqualTo(WebhookResult.ERROR);
        assertThat(recorded.getValue().message()).contains("referenceId không hợp lệ");
    }

    @Test
    @DisplayName("handle: ghi log lỗi -> phản hồi webhook thành công vẫn như cũ (không ném lỗi)")
    void handle_RecorderFails_SuccessPathUnaffected() {
        when(paymentService.processWebhook(any(Webhook.class))).thenReturn(outcome(WebhookResult.PROCESSED));
        doThrow(new RuntimeException("db down")).when(recorder).record(any(), anyInt(), anyLong(), any());

        assertThatCode(() -> webhookService.handle(VALID_BODY, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("handle: ghi log lỗi trong lúc webhook sai chữ ký -> vẫn ném lỗi gốc PAYMENT_WEBHOOK_INVALID")
    void handle_RecorderFails_FailurePathStillRethrowsOriginal() {
        when(paymentService.processWebhook(any(Webhook.class)))
                .thenThrow(new PaymentException(ErrorCode.PAYMENT_WEBHOOK_INVALID));
        doThrow(new RuntimeException("db down")).when(recorder).record(any(), anyInt(), anyLong(), any());

        assertThatThrownBy(() -> webhookService.handle(VALID_BODY, IP))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo("PAYMENT_WEBHOOK_INVALID");
    }
}
