package com.datn.financeapp.module.pay.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.pay.config.PayOsProperties;
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
import com.datn.financeapp.module.pay.helper.PaymentSpecs;
import com.datn.financeapp.module.pay.mapper.PaymentMapper;
import com.datn.financeapp.module.pay.repository.PaymentRepository;
import com.datn.financeapp.module.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final String SUCCESS_CODE = "00";
    private static final String CHECKOUT_BASE_URL = "https://pay.payos.vn/web/";
    private static final int MAX_DESCRIPTION_LENGTH = 9;
    private static final long DESCRIPTION_MOD = 10_000_000L;
    private static final long FIRST_ORDER_CODE = 100_000L;

    private final PaymentRepository paymentRepository;
    private final PayOsClientHelper payOsClientHelper;
    private final PayOsProperties properties;
    private final PaymentMapper paymentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    public PaymentCreateRes createPayment(PaymentCreateReq req) {
        Payment pending;
        try {
            pending = paymentRepository
                    .findPendingForUpdateNoWait(req.userId(), req.type(), PaymentStatus.PENDING)
                    .orElse(null);
        } catch (PessimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS);
        }

        if (pending == null) {
            return createNewPayment(req);
        }

        closeUnpaid(pending, isPendingAndOverdue(pending) ? PaymentStatus.EXPIRED : PaymentStatus.CANCELLED);
        if (pending.getStatus() != PaymentStatus.EXPIRED && pending.getStatus() != PaymentStatus.CANCELLED) {
            return toCreateRes(pending);
        }
        paymentRepository.flush();
        return createNewPayment(req);
    }

    @Override
    public PaymentStatusRes getStatus(UUID userId, long orderCode) {
        if (!paymentRepository.existsByOrderCodeAndUserId(orderCode, userId)) {
            throw new PaymentNotFoundException();
        }
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(PaymentNotFoundException::new);

        if (payment.getStatus() == PaymentStatus.PENDING) {
            syncPending(payment);
        }
        return paymentMapper.toStatusRes(payment);
    }

    @Override
    public PaymentStatusRes cancel(UUID userId, long orderCode) {
        if (!paymentRepository.existsByOrderCodeAndUserId(orderCode, userId)) {
            throw new PaymentNotFoundException();
        }
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(PaymentNotFoundException::new);

        if (payment.getStatus() == PaymentStatus.PENDING) {
            closeUnpaid(payment, PaymentStatus.CANCELLED);
        }
        return paymentMapper.toStatusRes(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentListRes listMine(UUID userId, PaymentStatus status, PaymentType type, Pageable pageable) {
        Page<Payment> page = paymentRepository.findAll(
                PaymentSpecs.filter(userId, status, type, null, null),
                newestFirst(pageable));
        return new PaymentListRes(page.map(paymentMapper::toStatusRes).getContent(), PageMeta.from(page));
    }

    @Override
    public void processWebhook(Webhook webhook) {
        WebhookData data = payOsClientHelper.verifyWebhook(webhook);
        if (data == null || data.getOrderCode() == null) {
            log.info("payOS webhook ignored: empty data");
            return;
        }
        if (!isSuccessfulWebhook(webhook, data)) {
            log.info("payOS webhook ignored: orderCode={}, code={}, dataCode={}",
                    data.getOrderCode(), webhook.getCode(), data.getCode());
            return;
        }

        Payment payment = paymentRepository.findByOrderCodeForUpdate(data.getOrderCode()).orElse(null);
        if (payment == null) {
            if (data.getOrderCode() >= FIRST_ORDER_CODE) {
                log.error("[PAYMENT-RECONCILE] không có đơn cho orderCode={}, amount={}, paymentLinkId={} (link mồ côi?)",
                        data.getOrderCode(), data.getAmount(), data.getPaymentLinkId());
            } else {
                log.info("payOS webhook thử/không phải của hệ thống: orderCode={}", data.getOrderCode());
            }
            return;
        }

        if (!sameLink(payment, data.getPaymentLinkId())) {
            log.error("[PAYMENT-RECONCILE] link không khớp: orderCode={}, savedLinkId={}, webhookLinkId={}",
                    payment.getOrderCode(), payment.getPayosLinkId(), data.getPaymentLinkId());
            return;
        }

        markPaid(payment, data.getAmount());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findOverdueOrderCodes(Instant now, int limit) {
        return paymentRepository.findOverdueOrderCodes(PaymentStatus.PENDING, now, PageRequest.of(0, limit));
    }

    @Override
    public void expireIfOverdue(long orderCode) {
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
        if (!isPendingAndOverdue(payment)) {
            return;
        }
        closeUnpaid(payment, PaymentStatus.EXPIRED);
    }

    // -------------------------------- PACKAGE-PRIVATE / PROTECTED FOR TEST --------------------------------

    static long receivedOf(PaymentLink link) {
        if (link == null) return 0L;
        long sum = link.getTransactions() == null ? 0L
                : link.getTransactions().stream().mapToLong(t -> t.getAmount() == null ? 0L : t.getAmount()).sum();
        long reported = link.getAmountPaid() == null ? 0L : link.getAmountPaid();
        return Math.max(sum, reported);
    }

    // -------------------------------- PRIVATE --------------------------------

    private void closeUnpaid(Payment payment, PaymentStatus targetStatus) {
        PaymentLink link = payOsClientHelper.getLink(payment.getOrderCode());
        long received = receivedOf(link);
        if (received >= payment.getAmount()) {
            markPaid(payment, received);
            return;
        }
        if (received > 0 || link.getStatus() == PaymentLinkStatus.PAID) {
            log.error("[PAYMENT-RECONCILE] link đã nhận {} < {} (status {}), không đóng đơn: orderCode={}",
                    received, payment.getAmount(), link.getStatus(), payment.getOrderCode());
            throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        if (!isDead(link.getStatus())) {
            PaymentLink cancelled = payOsClientHelper.cancelLink(payment.getOrderCode());
            long afterCancel = receivedOf(cancelled);
            if (afterCancel >= payment.getAmount()) {
                markPaid(payment, afterCancel);
                return;
            }
            if (afterCancel > 0 || !isDead(cancelled.getStatus())) {
                log.error("[PAYMENT-RECONCILE] huỷ link không hiệu lực hoặc có tiền vào: orderCode={}, status={}, received={}",
                        payment.getOrderCode(), cancelled.getStatus(), afterCancel);
                throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);
            }
        }
        payment.setStatus(targetStatus);
    }

    private void syncPending(Payment payment) {
        try {
            if (isPendingAndOverdue(payment)) {
                closeUnpaid(payment, PaymentStatus.EXPIRED);
                return;
            }
            PaymentLink link = payOsClientHelper.getLink(payment.getOrderCode());
            long received = receivedOf(link);
            if (received >= payment.getAmount()) {
                markPaid(payment, received);
                return;
            }
            if (received > 0 || link.getStatus() == PaymentLinkStatus.PAID) {
                log.error("[PAYMENT-RECONCILE] syncPending link đã nhận {} < {} (status {}): orderCode={}",
                        received, payment.getAmount(), link.getStatus(), payment.getOrderCode());
                return;
            }
            if (isDead(link.getStatus())) {
                if (link.getStatus() == PaymentLinkStatus.CANCELLED) {
                    payment.setStatus(PaymentStatus.CANCELLED);
                } else {
                    payment.setStatus(PaymentStatus.EXPIRED);
                }
            }
        } catch (PaymentException ex) {
            log.warn("payOS sync failed, keep PENDING: orderCode={}, code={}", payment.getOrderCode(), ex.getCode());
        }
    }

    private boolean isDead(PaymentLinkStatus status) {
        return status == PaymentLinkStatus.CANCELLED || status == PaymentLinkStatus.EXPIRED
                || status == PaymentLinkStatus.FAILED;
    }

    private PaymentCreateRes createNewPayment(PaymentCreateReq req) {
        long orderCode = paymentRepository.nextOrderCode();
        Instant expiresAt = clock.instant().plusSeconds(properties.expiredMinutes() * 60);

        String description = buildDescription(orderCode);
        CreatePaymentLinkResponse link = payOsClientHelper.createLink(
                orderCode, req.amount(), description, expiresAt.getEpochSecond());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        payOsClientHelper.cancelLink(orderCode);
                    } catch (Exception ex) {
                        log.error("[PAYMENT-RECONCILE] không huỷ được link mồ côi: orderCode={}", orderCode, ex);
                    }
                }
            }
        });

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderCode(orderCode)
                .userId(req.userId())
                .type(req.type())
                .referenceId(req.referenceId())
                .amount(req.amount())
                .status(PaymentStatus.PENDING)
                .payosLinkId(link.getPaymentLinkId())
                .expiresAt(expiresAt)
                .build();
        paymentRepository.saveAndFlush(payment);

        log.info("payOS payment link created: orderCode={}, type={}", orderCode, req.type());
        return paymentMapper.toCreateRes(payment, link.getCheckoutUrl());
    }

    private void markPaid(Payment payment, Long paidAmount) {
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.error("[PAYMENT-RECONCILE] nhận tiền cho đơn đã {}: orderCode={}, paid={}",
                    payment.getStatus(), payment.getOrderCode(), paidAmount);
            return;
        }
        if (paidAmount == null || paidAmount < payment.getAmount()) {
            log.error("[PAYMENT-RECONCILE] thiếu tiền: expected={}, paid={}, orderCode={}",
                    payment.getAmount(), paidAmount, payment.getOrderCode());
            return;
        }
        if (paidAmount > payment.getAmount()) {
            log.info("Payment {} trả dư {}", payment.getOrderCode(), paidAmount - payment.getAmount());
        }
        payment.setAmountPaid(paidAmount);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(clock.instant());
        paymentRepository.save(payment);
        eventPublisher.publishEvent(new PaymentPaidEvent(
                payment.getUserId(), payment.getType(), payment.getReferenceId(), payment.getOrderCode()));
        log.info("payOS payment success: orderCode={}", payment.getOrderCode());
    }

    private boolean isPendingAndOverdue(Payment payment) {
        return payment != null
                && payment.getStatus() == PaymentStatus.PENDING
                && !payment.getExpiresAt().isAfter(clock.instant());
    }

    private boolean sameLink(Payment payment, String webhookLinkId) {
        if (payment.getPayosLinkId() == null || webhookLinkId == null || webhookLinkId.isBlank()) {
            return true;
        }
        return payment.getPayosLinkId().equals(webhookLinkId);
    }

    private boolean isSuccessfulWebhook(Webhook webhook, WebhookData data) {
        return Boolean.TRUE.equals(webhook.getSuccess())
                && SUCCESS_CODE.equals(webhook.getCode())
                && SUCCESS_CODE.equals(data.getCode());
    }

    private PaymentCreateRes toCreateRes(Payment payment) {
        String checkoutUrl = payment.getPayosLinkId() == null ? null : CHECKOUT_BASE_URL + payment.getPayosLinkId();
        return paymentMapper.toCreateRes(payment, checkoutUrl);
    }

    private Pageable newestFirst(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String buildDescription(long orderCode) {
        String value = "DH" + orderCode;
        if (value.length() <= MAX_DESCRIPTION_LENGTH) {
            return value;
        }
        return "DH" + Math.floorMod(orderCode, DESCRIPTION_MOD);
    }
}
