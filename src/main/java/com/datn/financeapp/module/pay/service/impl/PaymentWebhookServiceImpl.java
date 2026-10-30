package com.datn.financeapp.module.pay.service.impl;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.pay.dto.res.WebhookOutcome;
import com.datn.financeapp.module.pay.exceptions.PaymentException;
import com.datn.financeapp.module.pay.service.PaymentService;
import com.datn.financeapp.module.pay.service.PaymentWebhookLogRecorder;
import com.datn.financeapp.module.pay.service.PaymentWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.payos.model.webhooks.Webhook;

import java.util.concurrent.TimeUnit;

/**
 * Cố ý KHÔNG đánh dấu {@code @Transactional}: transaction của {@code processWebhook} tự commit/rollback độc lập,
 * còn nhật ký ghi bằng transaction riêng nên luôn được lưu kể cả khi xử lý bị rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    // payOS gửi JSON camelCase, không đi qua naming strategy snake_case của Spring
    private static final ObjectMapper PAYOS_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final PaymentService paymentService;
    private final PaymentWebhookLogRecorder recorder;

    @Override
    public void handle(String rawBody, String clientIp) {
        long started = System.nanoTime();
        Webhook webhook = null;
        WebhookOutcome outcome;
        int httpStatus = 200;
        RuntimeException failure = null;

        try {
            webhook = parse(rawBody);
            outcome = paymentService.processWebhook(webhook);
        } catch (PaymentException ex) {
            failure = ex;
            httpStatus = ex.getHttpStatus();
            outcome = classify(webhook, ex);
        } catch (RuntimeException ex) {
            failure = ex;
            httpStatus = 500;
            outcome = WebhookOutcome.error(webhook, ex);
        }

        record(outcome, httpStatus, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), clientIp);
        if (failure != null) {
            throw failure;
        }
    }

    private Webhook parse(String body) {
        try {
            return PAYOS_MAPPER.readValue(body, Webhook.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new PaymentException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }
    }

    // Chưa parse được -> body hỏng; parse được mà bị từ chối bằng PAYMENT_WEBHOOK_INVALID -> sai chữ ký; còn lại là lỗi khác
    private WebhookOutcome classify(Webhook webhook, PaymentException ex) {
        if (webhook == null) {
            return WebhookOutcome.invalidBody("Body không phải JSON hợp lệ");
        }
        if (ErrorCode.PAYMENT_WEBHOOK_INVALID.getCode().equals(ex.getCode())) {
            return WebhookOutcome.invalidSignature(webhook);
        }
        return WebhookOutcome.error(webhook, ex);
    }

    // Lỗi ghi nhật ký không được làm đổi phản hồi của webhook
    private void record(WebhookOutcome outcome, int httpStatus, long durationMs, String clientIp) {
        try {
            recorder.record(outcome, httpStatus, durationMs, clientIp);
        } catch (RuntimeException ex) {
            log.error("Ghi nhật ký webhook thất bại: result={}", outcome.result(), ex);
        }
    }
}
