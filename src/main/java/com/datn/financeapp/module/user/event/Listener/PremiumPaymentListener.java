package com.datn.financeapp.module.user.event.Listener;

import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.events.publisher.PaymentPaidEvent;
import com.datn.financeapp.module.user.enums.PremiumPlan;
import com.datn.financeapp.module.user.exception.UserNotFoundException;
import com.datn.financeapp.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Giao hàng cho đơn nâng cấp premium khi thanh toán thành công.
 * Dùng {@code @EventListener} đồng bộ để chạy cùng transaction với webhook: nâng cấp lỗi thì cả việc ghi PAID rollback
 * và payOS gửi lại webhook.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PremiumPaymentListener {

    private final UserService userService;

    @EventListener
    public void onPaymentPaid(PaymentPaidEvent event) {
        if (event.type() != PaymentType.PREMIUM_UPGRADE) {
            return;
        }

        PremiumPlan plan;
        try {
            if (event.referenceId() == null || event.referenceId().isBlank()) {
                throw new IllegalArgumentException("referenceId trống");
            }
            plan = PremiumPlan.valueOf(event.referenceId());
        } catch (IllegalArgumentException ex) {
            log.error("[PAYMENT-RECONCILE] referenceId không hợp lệ: orderCode={}, referenceId={}",
                    event.orderCode(), event.referenceId(), ex);
            throw new IllegalStateException("referenceId không hợp lệ: " + event.referenceId(), ex);
        }

        try {
            userService.upgradeToPremium(event.userId(), plan.getMonths());
        } catch (UserNotFoundException ex) {
            log.error("[PAYMENT-RECONCILE] user không tồn tại khi nâng cấp premium: orderCode={}, userId={}",
                    event.orderCode(), event.userId(), ex);
            // bỏ qua không ném lỗi để tránh payOS gửi lại webhook mãi
        }
    }
}
