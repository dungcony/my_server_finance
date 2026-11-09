package com.datn.financeapp.module.pay.service.impl;

import com.datn.financeapp.module.pay.dto.res.WebhookOutcome;
import com.datn.financeapp.module.pay.entity.PaymentWebhookLog;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import com.datn.financeapp.module.pay.repository.PaymentWebhookLogRepository;
import com.datn.financeapp.module.pay.service.PaymentWebhookLogRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookLogRecorderImpl implements PaymentWebhookLogRecorder {

    static final int MAX_SPAM_ROWS_PER_MINUTE = 100;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final List<WebhookResult> SPAM_PRONE = List.of(WebhookResult.INVALID_BODY, WebhookResult.INVALID_SIGNATURE);

    private final PaymentWebhookLogRepository repository;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(WebhookOutcome outcome, int httpStatus, long durationMs, String clientIp) {
        if (outcome == null) {
            return;
        }
        try {
            Instant now = clock.instant();
            if (outcome.result().isSpamProne() && overSpamLimit(now)) {
                aggregate(outcome, httpStatus, durationMs, clientIp, now);
                return;
            }
            repository.save(PaymentWebhookLog.builder()
                    .receivedAt(now)
                    .orderCode(outcome.orderCode())
                    .paymentLinkId(outcome.paymentLinkId())
                    .amount(outcome.amount())
                    .payosCode(outcome.payosCode())
                    .success(outcome.success())
                    .result(outcome.result())
                    .message(truncate(outcome.message()))
                    .httpStatus(httpStatus)
                    .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                    .clientIp(clientIp)
                    .occurrences(1)
                    .aggregated(false)
                    .build());
        } catch (Exception ex) {
            log.error("Không ghi được nhật ký webhook: result={}, orderCode={}", outcome.result(), outcome.orderCode(), ex);
        }
    }

    // Quá giới hạn thì không bỏ dữ liệu mà gộp vào một dòng mỗi phút, để vẫn thấy được quy mô cuộc tấn công
    private boolean overSpamLimit(Instant now) {
        long count = repository.countByResultInAndAggregatedFalseAndReceivedAtGreaterThanEqual(
                SPAM_PRONE, now.truncatedTo(ChronoUnit.MINUTES));
        return count >= MAX_SPAM_ROWS_PER_MINUTE;
    }

    private void aggregate(WebhookOutcome outcome, int httpStatus, long durationMs, String clientIp, Instant now) {
        Instant minuteStart = now.truncatedTo(ChronoUnit.MINUTES);
        PaymentWebhookLog row = repository
                .findFirstByResultAndAggregatedTrueAndReceivedAtGreaterThanEqual(outcome.result(), minuteStart)
                .orElseGet(() -> PaymentWebhookLog.builder()
                        .receivedAt(now)
                        .result(outcome.result())
                        .httpStatus(httpStatus)
                        .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                        .clientIp(clientIp)
                        .occurrences(0)
                        .aggregated(true)
                        .build());
        row.setOccurrences(row.getOccurrences() + 1);
        row.setMessage("Đã gộp " + row.getOccurrences() + " request giả mạo trong phút này (vượt giới hạn "
                + MAX_SPAM_ROWS_PER_MINUTE + " dòng/phút)");
        repository.save(row);
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
