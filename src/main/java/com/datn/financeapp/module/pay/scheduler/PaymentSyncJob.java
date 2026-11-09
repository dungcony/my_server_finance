package com.datn.financeapp.module.pay.scheduler;

import com.datn.financeapp.module.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

/**
 * Tác vụ định kỳ kiểm tra biến động tiền vào cho các đơn thanh toán PENDING.
 * Hỗ trợ phát hiện trường hợp khách hàng chuyển thiếu tiền mà không phụ thuộc vào webhook payOS.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSyncJob {

    // giới hạn số lượng đơn quét mỗi chu kỳ
    static final int BATCH_SIZE = 50;

    private final PaymentService paymentService;
    private final Clock clock;

    // chạy định kỳ mỗi 1 phút một lần
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void syncPendingPayments() {
        // tìm các đơn PENDING còn trong thời hạn thanh toán
        for (Long orderCode : paymentService.findActivePendingOrderCodes(clock.instant(), BATCH_SIZE)) {
            try {
                // kiểm tra trạng thái từ cổng payOS xem có khoản tiền nào vừa chuyển vào không
                paymentService.syncPendingOrder(orderCode);
            } catch (Exception ex) {
                log.warn("Đồng bộ trạng thái đơn PENDING thất bại: orderCode={}", orderCode, ex);
            }
        }
    }
}
