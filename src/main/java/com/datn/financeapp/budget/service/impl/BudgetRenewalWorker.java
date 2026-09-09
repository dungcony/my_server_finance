package com.datn.financeapp.budget.service.impl;

import com.datn.financeapp.budget.service.BudgetService;

import com.datn.financeapp.budget.entity.Budget;
import com.datn.financeapp.budget.repository.BudgetRepository;
import com.datn.financeapp.category.dto.response.CategoryRefResponse;
import com.datn.financeapp.category.service.CategoryService;
import com.datn.financeapp.notification.service.NotificationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean riêng chỉ để {@code renewOneBudget} đi qua đúng Spring AOP proxy cho {@code @Transactional}
 * khi được gọi từ vòng lặp {@link BudgetService#renewExpiredBudgets()} — gọi trực tiếp method
 * {@code @Transactional} khác trong CÙNG class ({@code this.xxx()}) là self-invocation, bỏ qua
 * proxy và làm mất transaction hoàn toàn (bài học {@code IdempotencyTransactionHelper}, Phase 1).
 */
@Component
@RequiredArgsConstructor
class BudgetRenewalWorker {

    private final BudgetRepository budgetRepository;
    private final CategoryService categoryService;
    private final NotificationService notificationService;

    /**
     * Tạo kỳ mới nối tiếp kỳ đã hết hạn, tắt {@code is_active} của kỳ cũ. Chống trùng khi job chạy
     * lại (ví dụ tác vụ hôm qua lỗi giữa chừng) bằng kiểm tra {@link BudgetRepository#existsOverlapping}
     * TRƯỚC khi tạo — lớp bảo vệ Java, cộng thêm {@code ex_bud_no_overlap} là lớp bảo vệ CSDL cuối
     * cùng nếu Java race.
     */
    @Transactional
    void renewOneBudget(UUID oldBudgetId) {
        Budget old = budgetRepository.findById(oldBudgetId).orElseThrow();

        LocalDate newStart = old.getEndDate().plusDays(1);
        LocalDate newEnd = BudgetService.endOfPeriod(old.getPeriodType(), newStart);

        boolean exists = budgetRepository.existsOverlapping(
                old.getUserId(), old.getCategoryId(), old.getWalletId(), newStart, newEnd);
        if (exists) {
            return; // đã lặp rồi (job chạy lại), bỏ qua im lặng
        }

        old.setIsActive(false);
        budgetRepository.save(old);

        Budget renewed = Budget.builder()
                .id(UUID.randomUUID())
                .userId(old.getUserId())
                .groupId(old.getGroupId())
                .categoryId(old.getCategoryId())
                .walletId(old.getWalletId())
                .limitAmount(old.getLimitAmount())
                .periodType(old.getPeriodType())
                .startDate(newStart)
                .endDate(newEnd)
                .autoRenew(true)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        budgetRepository.save(renewed);

        CategoryRefResponse category =
                categoryService.findRefVisibleToUser(old.getCategoryId(), old.getUserId());
        String categoryName = category != null ? category.name() : "Danh mục đã xoá";
        notificationService.createNotification(
                old.getUserId(),
                "budget_renewed",
                "Ngân sách đã bắt đầu kỳ mới",
                "Ngân sách " + categoryName + " đã tự động chuyển sang kỳ mới từ "
                        + String.format("%02d/%02d", newStart.getDayOfMonth(), newStart.getMonthValue())
                        + " đến " + String.format("%02d/%02d", newEnd.getDayOfMonth(), newEnd.getMonthValue()) + ".",
                renewed.getId());
    }
}
