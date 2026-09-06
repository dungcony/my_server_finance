package com.datn.financeapp.debt.service;

import com.datn.financeapp.debt.entity.Debt;
import com.datn.financeapp.notification.service.NotificationService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean riêng chỉ để {@code evaluateAndNotify} đi qua đúng Spring AOP proxy cho
 * {@code @Transactional} khi được gọi từ vòng lặp {@link DebtService#sendDueReminders()} — gọi
 * trực tiếp method {@code @Transactional} khác trong CÙNG class ({@code this.xxx()}) là self-
 * invocation, bỏ qua proxy và làm mất transaction hoàn toàn (bài học
 * {@code IdempotencyTransactionHelper}, Phase 1).
 */
@Component
@RequiredArgsConstructor
class DebtReminderWorker {

    /**
     * {@code @Scheduled(zone = "Asia/Ho_Chi_Minh")} chỉ quyết định GIỜ CHẠY job, không đổi múi
     * giờ mặc định của JVM. {@code LocalDate.now()} trần vẫn đọc theo JVM — trên container chạy
     * UTC, job 4h sáng VN rơi vào 21h ngày HÔM TRƯỚC theo UTC, nên mọi mốc nhắc lệch đúng một
     * ngày mà chỉ lộ ra trên máy chủ thật (máy phát triển giờ VN chạy đúng). Neo thẳng vào múi
     * giờ Việt Nam như {@code TransactionService.VIETNAM_ZONE}.
     */
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NotificationService notificationService;

    /** api/08 mục 9 — ba mốc nhắc: còn 7 ngày, còn 1 ngày, quá hạn (nhắc lại mỗi 7 ngày). */
    @Transactional
    void evaluateAndNotify(Debt debt) {
        long daysUntilDue =
                ChronoUnit.DAYS.between(LocalDate.now(VIETNAM_ZONE), debt.getDueDate());
        String content;
        if (daysUntilDue == 7) {
            content = "Còn 7 ngày đến hạn khoản nợ với " + debt.getCounterpartyName() + ".";
        } else if (daysUntilDue == 1) {
            content = "Ngày mai đến hạn khoản nợ với " + debt.getCounterpartyName() + ".";
        } else if (daysUntilDue <= 0 && (-daysUntilDue) % 7 == 0) {
            // {@code <= 0} chứ không phải {@code < 0}: mốc quá hạn ĐẦU TIÊN là chính ngày đến hạn
            // ({@code daysUntilDue == 0}). Điều kiện cũ bỏ qua ca này, người vỡ hạn hôm nay im
            // lặng tới tận 7 ngày sau mới được nhắc — đúng lúc cần nhắc nhất thì không có gì.
            content = daysUntilDue == 0
                    ? "Hôm nay đến hạn khoản nợ với " + debt.getCounterpartyName() + "."
                    : "Đã quá hạn " + (-daysUntilDue) + " ngày khoản nợ với "
                            + debt.getCounterpartyName() + ".";
        } else {
            return; // không rơi vào mốc nhắc nào trong ba mốc trên
        }
        notificationService.createDebtReminder(
                debt.getUserId(), "Nhắc nợ đến hạn", content, debt.getId());
    }
}
