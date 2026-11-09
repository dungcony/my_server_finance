package com.datn.financeapp.module.pay.scheduler;

import com.datn.financeapp.module.pay.config.PaymentTopupProperties;
import com.datn.financeapp.module.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Tác vụ định kỳ kiểm tra hạn nạp bù tiền và gửi thông báo nhắc nhở cho các đơn PARTIALLY_PAID.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentTopupExpiryJob {

    // số lượng đơn xử lý tối đa mỗi đợt quét
    static final int BATCH_SIZE = 50;

    private final PaymentService paymentService;
    private final PaymentTopupProperties topupProperties;
    private final Clock clock;

    // chạy định kỳ theo cấu hình giây (mặc định 60 giây)
    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void processTopupDeadlines() {
        Instant now = clock.instant();

        // xử lý các đơn thiếu tiền đã vượt quá hạn bù: huỷ link và giữ tiền
        for (Long orderCode : paymentService.findOverdueTopupOrderCodes(now, BATCH_SIZE)) {
            try {
                paymentService.forfeitIfTopupOverdue(orderCode);
            } catch (Exception ex) {
                log.error("Xử lý phạt đơn thiếu tiền thất bại: orderCode={}", orderCode, ex);
            }
        }

        // nếu cấu hình có thời gian nhắc nhở trước hạn
        if (topupProperties.reminderSeconds() > 0) {
            Instant reminderThreshold = now.plusSeconds(topupProperties.reminderSeconds());

            // tìm và gửi thông báo nhắc nhở cho các đơn sắp tới hạn chót
            for (Long orderCode : paymentService.findTopupReminderCandidates(now, reminderThreshold, BATCH_SIZE)) {
                try {
                    paymentService.remindTopupIfCandidate(orderCode, now, reminderThreshold);
                } catch (Exception ex) {
                    log.error("Gửi thông báo nhắc hạn bù tiền thất bại: orderCode={}", orderCode, ex);
                }
            }
        }
    }
}
