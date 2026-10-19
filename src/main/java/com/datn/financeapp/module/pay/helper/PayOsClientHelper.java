package com.datn.financeapp.module.pay.helper;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.pay.config.PayOsProperties;
import com.datn.financeapp.module.pay.exceptions.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.time.Instant;

/**
 * Bọc SDK payOS: gom việc khởi tạo client và đổi {@link PayOSException} thành {@link PaymentException}.
 * Tách thành bean riêng để test tích hợp thay bằng mock thay vì gọi payOS thật.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayOsClientHelper {

    private final PayOsProperties properties;

    private volatile PayOS payOS;

    public CreatePaymentLinkResponse createLink(long orderCode, long amount, String description, long expiredAtEpochSecond) {
        if (orderCode <= 0) {
            throw new IllegalArgumentException("orderCode phải lớn hơn 0");
        }
        if (amount < 2000) {
            throw new IllegalArgumentException("amount phải tối thiểu 2000 VND");
        }
        if (expiredAtEpochSecond <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("expiredAt phải nằm trong tương lai");
        }
        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(description)
                .cancelUrl(properties.resolvedCancelUrl())
                .returnUrl(properties.resolvedReturnUrl())
                .expiredAt(expiredAtEpochSecond)
                .build();
        try {
            return client().paymentRequests().create(request);
        } catch (PayOSException ex) {
            log.warn("payOS create link failed: orderCode={}, error={}", orderCode, ex.getMessage());
            throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    public PaymentLink getLink(long orderCode) {
        try {
            return client().paymentRequests().get(orderCode);
        } catch (PayOSException ex) {
            log.warn("payOS get link failed: orderCode={}, error={}", orderCode, ex.getMessage());
            throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    public PaymentLink cancelLink(long orderCode) {
        try {
            return client().paymentRequests().cancel(orderCode);
        } catch (PayOSException ex) {
            log.warn("payOS cancel link failed: orderCode={}, error={}", orderCode, ex.getMessage());
            throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    public WebhookData verifyWebhook(Webhook webhook) {
        try {
            return client().webhooks().verify(webhook);
        } catch (PayOSException ex) {
            log.warn("payOS webhook verify failed: error={}", ex.getMessage());
            throw new PaymentException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }
    }

    private PayOS client() {
        if (!properties.configured()) {
            throw new PaymentException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        PayOS current = payOS;
        if (current == null) {
            synchronized (this) {
                current = payOS;
                if (current == null) {
                    ClientOptions.ClientOptionsBuilder builder = ClientOptions.builder()
                            .clientId(properties.clientId())
                            .apiKey(properties.apiKey())
                            .checksumKey(properties.checksumKey());
                    if (properties.resolvedBaseUrl() != null) {
                        builder.baseURL(properties.resolvedBaseUrl());
                    }
                    current = new PayOS(builder.build());
                    payOS = current;
                }
            }
        }
        return current;
    }
}
