package com.datn.financeapp.module.notification.events.listener;

import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;
import com.datn.financeapp.module.pay.events.publisher.PaymentForfeitedEvent;
import com.datn.financeapp.module.pay.events.publisher.PaymentPartiallyPaidEvent;
import com.datn.financeapp.module.pay.events.publisher.PaymentTopupReminderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Lắng nghe các sự kiện thanh toán thiếu tiền từ phân hệ payOS để gửi thông báo in-app cho người dùng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationListener {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(VN_ZONE);
    private static final Locale VN_LOCALE = Locale.forLanguageTag("vi-VN");

    private final NotificationService notificationService;

    // lắng nghe sự kiện khi đơn thanh toán bị thiếu tiền
    @EventListener
    public void onPaymentPartiallyPaid(PaymentPartiallyPaidEvent event) {
        String formattedPaid = formatMoney(event.amountPaid());
        String formattedTotal = formatMoney(event.amount());
        String formattedRemaining = formatMoney(event.amountRemaining());
        String formattedDeadline = formatTime(event.topupDeadline());

        String title = "Thanh toán chưa đủ";
        String content = "Đơn #" + event.orderCode() + " mới nhận " + formattedPaid
                + " trên tổng " + formattedTotal + ". Vui lòng thanh toán nốt " + formattedRemaining
                + " trước " + formattedDeadline + ". Quá hạn, số tiền đã nhận sẽ không được hoàn lại.";

        Map<String, Object> data = Map.of(
                "order_code", event.orderCode(),
                "amount_paid", event.amountPaid(),
                "amount_remaining", event.amountRemaining(),
                "topup_deadline", event.topupDeadline().toString()
        );

        // tạo thông báo hiển thị trong hòm thư thông báo của ứng dụng
        notificationService.createNotification(event.userId(), NotiType.PAYMENT_TOPUP_REQUIRED, title, content, null, data);
        log.info("Đã tạo thông báo thiếu tiền cho user={}, orderCode={}", event.userId(), event.orderCode());
    }

    // lắng nghe sự kiện khi sắp hết hạn nạp bù tiền
    @EventListener
    public void onPaymentTopupReminder(PaymentTopupReminderEvent event) {
        String formattedPaid = formatMoney(event.amountPaid());
        String formattedRemaining = formatMoney(event.amountRemaining());
        String formattedDeadline = formatTime(event.topupDeadline());

        String title = "Sắp hết hạn bù tiền";
        String content = "Còn ít thời gian để thanh toán nốt " + formattedRemaining
                + " cho đơn #" + event.orderCode() + " (hạn chót: " + formattedDeadline
                + "). Quá hạn, số tiền đã nhận (" + formattedPaid + ") sẽ không được hoàn lại.";

        Map<String, Object> data = Map.of(
                "order_code", event.orderCode(),
                "amount_paid", event.amountPaid(),
                "amount_remaining", event.amountRemaining(),
                "topup_deadline", event.topupDeadline().toString()
        );

        // gửi thông báo nhắc nhở hạn chót
        notificationService.createNotification(event.userId(), NotiType.PAYMENT_TOPUP_REMINDER, title, content, null, data);
        log.info("Đã tạo thông báo nhắc hạn bù tiền cho user={}, orderCode={}", event.userId(), event.orderCode());
    }

    // lắng nghe sự kiện khi đơn quá hạn bù bị đóng và giữ tiền
    @EventListener
    public void onPaymentForfeited(PaymentForfeitedEvent event) {
        String formattedPaid = formatMoney(event.amountPaid());

        String title = "Đơn thanh toán đã đóng";
        String content = "Đơn #" + event.orderCode() + " đã quá hạn bù tiền nên không được kích hoạt, số tiền đã nhận ("
                + formattedPaid + ") không được hoàn lại. Cần hỗ trợ, vui lòng liên hệ chúng tôi và cung cấp mã đơn.";

        Map<String, Object> data = Map.of(
                "order_code", event.orderCode(),
                "amount_paid", event.amountPaid(),
                "forfeited_at", event.forfeitedAt().toString()
        );

        // gửi thông báo thông tin đơn hàng đã bị đóng phạt
        notificationService.createNotification(event.userId(), NotiType.PAYMENT_FORFEITED, title, content, null, data);
        log.info("Đã tạo thông báo đơn bị đóng và giữ tiền cho user={}, orderCode={}", event.userId(), event.orderCode());
    }

    // định dạng tiền tệ theo chuẩn VND (ví dụ: 3.000đ)
    private String formatMoney(long amount) {
        NumberFormat nf = NumberFormat.getInstance(VN_LOCALE);
        return nf.format(amount) + "đ";
    }

    // định dạng thời gian theo giờ Việt Nam
    private String formatTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        return TIME_FORMATTER.format(instant);
    }
}
