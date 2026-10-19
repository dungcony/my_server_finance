package com.datn.financeapp.module.budget.service.impl;

import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.BudgetSpentCalculator;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.budget.service.BudgetRenewalService;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Triển khai {@link BudgetRenewalService}. Mỗi ngân sách gia hạn trong một transaction riêng bằng TransactionTemplate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetRenewalImpl implements BudgetRenewalService {

    private static final String UNKNOWN_CATEGORY_NAME = "Danh mục đã xoá";

    private final BudgetRepository budgetRepository;
    private final BudgetCategoryHelper categoryHelper;
    private final NotificationService notificationService;
    private final BudgetSpentCalculator spentCalculator;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void renewExpiredBudgets() {
        List<UUID> expiredIds = budgetRepository.findExpiredAutoRenewIds(LocalDate.now(UserTimezoneHelper.DEFAULT_ZONE));
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
        if (old == null || !old.getIsActive() || old.getIsDeleted()) {
            return;
        }

        LocalDate newStart = old.getEndDate().plusDays(1);
        LocalDate newEnd = old.getPeriodType().endOf(newStart);

        if (budgetRepository.existsOverlapping(old.getUserId(), old.getCategoryCode(), newStart, newEnd)) {
            return;
        }

        old.setIsActive(false);

        Budget renewed = Budget.builder()
                .id(UUID.randomUUID())
                .userId(old.getUserId())
                .categoryCode(old.getCategoryCode())
                .spent(spentCalculator.spentInPeriod(old.getUserId(), old.getCategoryCode(), newStart, newEnd, UserTimezoneHelper.DEFAULT_ZONE))
                .limitAmount(old.getLimitAmount())
                .periodType(old.getPeriodType())
                .startDate(newStart)
                .endDate(newEnd)
                .autoRenew(true)
                .isActive(true)
                .build();
        budgetRepository.save(renewed);

        String categoryName = categoryHelper.findSummary(old.getCategoryCode())
                .map(CateSumaryRes::name)
                .orElse(UNKNOWN_CATEGORY_NAME);

        notificationService.createNotification(
                old.getUserId(),
                NotiType.BUDGET_RENEWED,
                "Ngân sách đã bắt đầu kỳ mới",
                "Ngân sách " + categoryName + " đã tự động chuyển sang kỳ mới từ "
                        + newStart + " đến " + newEnd + ".",
                renewed.getId());
    }
}
