package com.datn.financeapp.unitTest.notification.events.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.events.listener.PaymentNotificationListener;
import com.datn.financeapp.module.notification.service.NotificationService;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.events.publisher.PaymentForfeitedEvent;
import com.datn.financeapp.module.pay.events.publisher.PaymentPartiallyPaidEvent;
import com.datn.financeapp.module.pay.events.publisher.PaymentTopupReminderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationListenerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant DEADLINE = Instant.parse("2026-10-22T08:00:00Z");

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PaymentNotificationListener listener;

    @Test
    @DisplayName("onPaymentPartiallyPaid: tạo thông báo yêu cầu nạp bù tiền khi nhận sự kiện thiếu tiền")
    void onPaymentPartiallyPaid_CreatesNotification() {
        PaymentPartiallyPaidEvent event = new PaymentPartiallyPaidEvent(
                USER_ID, PaymentType.PREMIUM_UPGRADE, "PREMIUM_1M",
                200003L, 4000L, 3000L, 1000L, DEADLINE);

        listener.onPaymentPartiallyPaid(event);

        verify(notificationService).createNotification(
                eq(USER_ID),
                eq(NotiType.PAYMENT_TOPUP_REQUIRED),
                eq("Thanh toán chưa đủ"),
                anyString(),
                isNull(),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("onPaymentTopupReminder: tạo thông báo nhắc nhở khi sắp hết hạn bù tiền")
    void onPaymentTopupReminder_CreatesNotification() {
        PaymentTopupReminderEvent event = new PaymentTopupReminderEvent(
                USER_ID, PaymentType.PREMIUM_UPGRADE, "PREMIUM_1M",
                200003L, 4000L, 3000L, 1000L, DEADLINE);

        listener.onPaymentTopupReminder(event);

        verify(notificationService).createNotification(
                eq(USER_ID),
                eq(NotiType.PAYMENT_TOPUP_REMINDER),
                eq("Sắp hết hạn bù tiền"),
                anyString(),
                isNull(),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("onPaymentForfeited: tạo thông báo khi đơn quá hạn bù bị đóng và giữ tiền")
    void onPaymentForfeited_CreatesNotification() {
        PaymentForfeitedEvent event = new PaymentForfeitedEvent(
                USER_ID, PaymentType.PREMIUM_UPGRADE, "PREMIUM_1M",
                200003L, 4000L, 3000L, DEADLINE);

        listener.onPaymentForfeited(event);

        verify(notificationService).createNotification(
                eq(USER_ID),
                eq(NotiType.PAYMENT_FORFEITED),
                eq("Đơn thanh toán đã đóng"),
                anyString(),
                isNull(),
                any(Map.class)
        );
    }
}
