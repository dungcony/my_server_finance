package com.datn.financeapp.module.pay.service.impl;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.pay.config.PaymentWebhookLogProperties;
import com.datn.financeapp.module.pay.dto.res.WebhookLogListRes;
import com.datn.financeapp.module.pay.dto.res.WebhookSummaryRes;
import com.datn.financeapp.module.pay.entity.PaymentWebhookLog;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import com.datn.financeapp.module.pay.helper.PaymentWebhookLogSpecs;
import com.datn.financeapp.module.pay.mapper.PaymentWebhookLogMapper;
import com.datn.financeapp.module.pay.repository.PaymentWebhookLogRepository;
import com.datn.financeapp.module.pay.service.PaymentWebhookLogAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentWebhookLogAdminServiceImpl implements PaymentWebhookLogAdminService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MIN_HOURS = 1;
    private static final int MAX_HOURS = 168;
    private static final int MAX_MISSING_LISTED = 50;

    private final PaymentWebhookLogRepository repository;
    private final PaymentWebhookLogMapper mapper;
    private final PaymentWebhookLogProperties properties;
    private final Clock clock;

    @Override
    public WebhookLogListRes list(WebhookResult result, boolean onlyFailed, Long orderCode,
                                  LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(VN_ZONE).toInstant();
        Instant to = toDate == null ? null : toDate.plusDays(1).atStartOfDay(VN_ZONE).toInstant();

        Page<PaymentWebhookLog> page = repository.findAll(
                PaymentWebhookLogSpecs.filter(result, onlyFailed, orderCode, from, to),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "receivedAt")));

        return new WebhookLogListRes(page.map(mapper::toRes).getContent(), PageMeta.from(page));
    }

    @Override
    public WebhookSummaryRes summary(int hours) {
        int window = Math.max(MIN_HOURS, Math.min(MAX_HOURS, hours));
        Instant now = clock.instant();
        Instant since = now.minus(window, ChronoUnit.HOURS);

        Map<WebhookResult, Long> counts = new EnumMap<>(WebhookResult.class);
        for (WebhookResult value : WebhookResult.values()) {
            counts.put(value, 0L);
        }
        repository.countGroupedByResult(since).forEach(row -> counts.put(row.getResult(), row.getTotal()));

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long failed = counts.entrySet().stream()
                .filter(entry -> entry.getKey().isFailure())
                .mapToLong(Map.Entry::getValue)
                .sum();
        double failureRate = total == 0 ? 0.0 : (double) failed / total;

        Instant lastSuccessAt = repository.lastReceivedAt(WebhookResult.confirmations());
        Instant lastFailureAt = repository.lastReceivedAt(WebhookResult.failures());

        List<Long> paidWithoutWebhook = repository.findPaidWithoutWebhook(
                since,
                now.minus(properties.missingMinutes(), ChronoUnit.MINUTES),
                WebhookResult.confirmations(),
                PageRequest.of(0, MAX_MISSING_LISTED));

        return new WebhookSummaryRes(window, total, failed, failureRate, counts, lastSuccessAt, lastFailureAt,
                paidWithoutWebhook, properties.missingMinutes());
    }
}
