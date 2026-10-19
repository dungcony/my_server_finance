package com.datn.financeapp.module.pay.scheduler;

import com.datn.financeapp.module.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

/**
 * Dọn các đơn thanh toán PENDING đã quá hạn: huỷ link payOS rồi ghi EXPIRED (hoặc PAID nếu payOS báo đã trả).
 * Mỗi đơn xử lý trong một transaction riêng qua {@link PaymentService}, một đơn lỗi không chặn các đơn sau.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryJob {

    static final int BATCH_SIZE = 100;

    private final PaymentService paymentService;
    private final Clock clock;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void expireOverduePayments() {
        for (Long orderCode : paymentService.findOverdueOrderCodes(clock.instant(), BATCH_SIZE)) {
            try {
                paymentService.expireIfOverdue(orderCode);
            } catch (Exception ex) {
                log.error("Expire payment failed: orderCode={}", orderCode, ex);
            }
        }
    }
}
