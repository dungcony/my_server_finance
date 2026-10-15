package com.datn.financeapp.module.budget.service.impl;

import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.helper.BudgetSpentCalculator;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.budget.service.BudgetRenewalService;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Triển khai {@link BudgetRenewalService}. Mỗi ngân sách gia hạn trong một transaction riêng bằng
 * {@link TransactionTemplate} (không dùng {@code @Transactional} ở hàm chính vì một cái lỗi không được
 * rollback cả lô). Mỗi lần chạy chỉ gia hạn một kỳ cho mỗi ngân sách; job bỏ lỡ nhiều kỳ sẽ bắt kịp dần
 * qua các lần chạy sau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetRenewalImpl implements BudgetRenewalService {

    private static final String UNKNOWN_CATEGORY_NAME = "Danh mục đã xoá";

    private final BudgetRepository budgetRepository;
    private final CategoryService categoryService;
    private final NotificationService notificationService;
    private final BudgetSpentCalculator spentCalculator;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void renewExpiredBudgets() {
        List<UUID> expiredIds = budgetRepository.findExpiredAutoRenewIds(LocalDate.now());
        for (UUID budgetId : expiredIds) {
            try {
                transactionTemplate.executeWithoutResult(status -> renewOne(budgetId));
            } catch (Exception e) {
                log.error("Lỗi khi gia hạn ngân sách {}", budgetId, e);
            }
        }
    }

    private void renewOne(UUID budgetId) {
        Budget old = budgetRepository.findById(budgetId).orElse(null);
        if (old == null || !old.getIsActive()) {
            return;
        }

        LocalDate newStart = old.getEndDate().plusDays(1);
        LocalDate newEnd = old.getPeriodType().endOf(newStart);
        // Job chạy lại sau lỗi: kỳ mới đã có thì bỏ qua, không tạo trùng
        if (budgetRepository.existsOverlapping(old.getUserId(), old.getCategoryId(), newStart, newEnd)) {
            return;
        }

        old.setIsActive(false);
        // Kỳ mới có thể đã có giao dịch (ghi trước ngày, hoặc job chạy muộn) nên spent phải tính lại
        Budget renewed = Budget.builder()
                .id(UUID.randomUUID())
                .userId(old.getUserId())
                .categoryId(old.getCategoryId())
                .spent(spentCalculator.spentInPeriod(old.getUserId(), old.getCategoryId(), newStart, newEnd))
                .limitAmount(old.getLimitAmount())
                .periodType(old.getPeriodType())
                .startDate(newStart)
                .endDate(newEnd)
                .autoRenew(true)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        budgetRepository.save(renewed);

        notificationService.createNotification(
                old.getUserId(),
                NotiType.BUDGET_RENEWED,
                "Ngân sách đã bắt đầu kỳ mới",
                "Ngân sách " + categoryNameOf(old.getCategoryId()) + " đã tự động chuyển sang kỳ mới từ "
                        + newStart + " đến " + newEnd + ".",
                renewed.getId());
    }

    private String categoryNameOf(UUID categoryId) {
        CategoryRes category = categoryService.findAllByIds(Set.of(categoryId)).get(categoryId);
        return category != null ? category.name() : UNKNOWN_CATEGORY_NAME;
    }
}
