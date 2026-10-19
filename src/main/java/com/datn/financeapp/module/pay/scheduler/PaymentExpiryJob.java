package com.datn.financeapp.module.pay.scheduler;

import com.datn.financeapp.module.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

/**
 * Tác vụ định kỳ dọn dẹp các đơn thanh toán PENDING đã quá hạn ban đầu.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryJob {

    // kích thước lô xử lý mỗi lần quét
    static final int BATCH_SIZE = 100;

    private final PaymentService paymentService;
    private final Clock clock;

    // chạy định kỳ mỗi 5 phút một lần để xử lý các đơn quá hạn
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void expireOverduePayments() {
        // lấy danh sách các đơn PENDING đã quá hạn thanh toán
        for (Long orderCode : paymentService.findOverdueOrderCodes(clock.instant(), BATCH_SIZE)) {
            try {
                // xử lý hết hạn cho từng đơn riêng biệt để lỗi một đơn không ảnh hưởng đơn khác
                paymentService.expireIfOverdue(orderCode);
            } catch (Exception ex) {
                log.error("Xử lý hết hạn đơn thanh toán thất bại: orderCode={}", orderCode, ex);
            }
        }
    }
}
