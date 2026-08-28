package com.datn.financeapp.debt.service;

import com.datn.financeapp.debt.entity.Debt;
import com.datn.financeapp.notification.repository.NotificationRepository;
import java.time.LocalDate;
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

    private final NotificationRepository notificationRepository;

    /** api/08 mục 9 — ba mốc nhắc: còn 7 ngày, còn 1 ngày, quá hạn (nhắc lại mỗi 7 ngày). */
    @Transactional
    void evaluateAndNotify(Debt debt) {
        long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), debt.getDueDate());
        String content;
        if (daysUntilDue == 7) {
            content = "Còn 7 ngày đến hạn khoản nợ với " + debt.getCounterpartyName() + ".";
        } else if (daysUntilDue == 1) {
            content = "Ngày mai đến hạn khoản nợ với " + debt.getCounterpartyName() + ".";
        } else if (daysUntilDue < 0 && daysUntilDue % 7 == 0) {
            content = "Đã quá hạn khoản nợ với " + debt.getCounterpartyName() + ".";
        } else {
            return; // không rơi vào mốc nhắc nào trong ba mốc trên
        }
        notificationRepository.insertGenericNotification(
                debt.getUserId(), "debt_reminder", "Nhắc nợ đến hạn", content, debt.getId());
    }
}
