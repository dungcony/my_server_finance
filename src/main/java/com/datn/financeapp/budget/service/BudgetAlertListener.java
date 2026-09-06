package com.datn.financeapp.budget.service;

import com.datn.financeapp.budget.repository.BudgetProgressRepository;
import com.datn.financeapp.budget.repository.BudgetProgressRepository.BudgetProgressProjection;
import com.datn.financeapp.notification.service.NotificationService;
import com.datn.financeapp.transaction.event.TransactionRecordedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * D-41 — sinh cảnh báo ngân sách vào bảng {@code notifications} ngay khi người dùng ghi một khoản
 * chi làm ngân sách chạm ngưỡng.
 *
 * <p><b>Vì sao AFTER_COMMIT chứ không gọi trực tiếp từ {@code TransactionService}:</b>
 *
 * <ul>
 *   <li>không đảo chiều phụ thuộc — {@code budget/} phụ thuộc {@code transaction/}, gọi ngược lại
 *       tạo vòng tròn;
 *   <li>không làm chậm luồng ghi — listener chạy NGOÀI transaction ghi giao dịch, giữ nguyên tắc
 *       dự án "ghi nhanh hơn quên, dưới 5 giây";
 *   <li>không có nguy cơ cảnh báo cho giao dịch chưa thực sự tồn tại — transaction rollback thì
 *       handler không bao giờ được gọi.
 * </ul>
 *
 * <p><b>Quan hệ với {@code GET /budgets/alerts}:</b> hai nguồn cố ý khác nhau, đừng đồng bộ chúng.
 * Endpoint kia là TRẠNG THÁI HIỆN TẠI (tính tại chỗ mỗi lần gọi); bản ghi ở đây là LỊCH SỬ tại
 * thời điểm vượt ngưỡng, giống tin nhắn ngân hàng — không tự sửa lại khi người dùng xoá giao dịch
 * sau đó.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertListener {

    private final BudgetProgressRepository budgetProgressRepository;
    private final NotificationService notificationService;

    /**
     * {@code @Transactional(REQUIRES_NEW)} là BẮT BUỘC, không phải trang trí: transaction gốc đã
     * commit và đóng trước khi handler này chạy, nên không còn transaction nào để {@code @Modifying}
     * INSERT bám vào — thiếu annotation sẽ ném {@code TransactionRequiredException}. Transaction
     * mới cũng đúng về mặt ngữ nghĩa: cảnh báo lỗi thì giao dịch của người dùng vẫn phải giữ
     * nguyên.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTransactionRecorded(TransactionRecordedEvent event) {
        // Chỉ khoản CHI có danh mục mới ảnh hưởng ngân sách. income không tính vào hạn mức chi, và
        // transfer thì không phải thu cũng không phải chi (CLAUDE.md §2).
        if (!"expense".equals(event.type()) || event.categoryId() == null) {
            return;
        }

        try {
            // Truy vấn đã tự lo việc cộng gộp danh mục con qua fn_category_tree: tìm mọi ngân sách
            // mà danh mục của giao dịch nằm TRONG cây danh mục của ngân sách đó. Lọc bằng so sánh
            // categoryId bằng nhau ở Java sẽ bỏ sót ca "ghi vào danh mục CON, ngân sách đặt ở danh
            // mục CHA" — lỗi im lặng, không có gì báo.
            List<BudgetProgressProjection> affected = budgetProgressRepository.findActiveByUserAndCategoryInTree(
                    event.userId(), event.categoryId(), event.date());

            for (BudgetProgressProjection budget : affected) {
                if ("normal".equals(budget.getStatus())) {
                    continue;
                }

                // Chống trùng nằm ở UNIQUE tầng CSDL (uq_notif_budget_alert: user, reference,
                // ngày, loại) qua ON CONFLICT DO NOTHING — KHÔNG kiểm tra tồn tại trước ở Java.
                // Bulk 50 dòng bắn 50 event, kiểm tra ở Java sẽ có race condition giữa các event
                // xử lý sát nhau và lọt vài bản ghi trùng.
                notificationService.createBudgetAlert(
                        event.userId(), buildTitle(budget), buildContent(budget), budget.getId());
            }
        } catch (Exception e) {
            // Transaction ghi giao dịch ĐÃ commit — không còn gì để rollback, và cũng không được
            // phép làm hỏng response của người dùng chỉ vì cảnh báo lỗi. Chỉ ghi log.
            log.error("Lỗi khi xử lý cảnh báo ngân sách cho giao dịch {}", event.transactionId(), e);
        }
    }

    private String buildTitle(BudgetProgressProjection budget) {
        return "over_limit".equals(budget.getStatus()) ? "Đã vượt ngân sách" : "Sắp hết ngân sách";
    }

    private String buildContent(BudgetProgressProjection budget) {
        long remaining = budget.getRemaining() == null ? 0L : budget.getRemaining();
        int daysRemaining = budget.getDaysRemaining() == null ? 0 : budget.getDaysRemaining();

        if ("over_limit".equals(budget.getStatus())) {
            return "Vượt " + BudgetService.formatAmount(Math.abs(remaining)) + " đ khi kỳ còn " + daysRemaining
                    + " ngày.";
        }
        return "Còn " + BudgetService.formatAmount(remaining) + " đ cho " + daysRemaining + " ngày còn lại của kỳ.";
    }
}
