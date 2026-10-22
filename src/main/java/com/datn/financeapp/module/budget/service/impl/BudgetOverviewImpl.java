package com.datn.financeapp.module.budget.service.impl;

import com.datn.financeapp.module.budget.dto.response.BudgetAlertRes;
import com.datn.financeapp.module.budget.dto.response.BudgetSummaryRes;
import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.BudgetStatus;
import com.datn.financeapp.module.budget.helper.BudgetAlertText;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.BudgetProgress;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.budget.service.BudgetOverviewService;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai {@link BudgetOverviewService}. Chỉ đọc; trạng thái tính từ {@link BudgetProgress}.
 */
@Service
@RequiredArgsConstructor
public class BudgetOverviewImpl implements BudgetOverviewService {

    private static final String SEVERITY_CRITICAL = "critical";
    private static final String SEVERITY_ALERT = "alert";
    private static final String UNKNOWN_CATEGORY_NAME = "Danh mục đã xoá";

    private final BudgetRepository budgetRepository;
    private final BudgetCategoryHelper categoryHelper;
    private final UserTimezoneHelper timezoneHelper;

    @Override
    @Transactional(readOnly = true)
    public BudgetSummaryRes summary(UUID userId) {
        ZoneId zone = timezoneHelper.getCurrentZone();
        LocalDate today = LocalDate.now(zone);
        List<Budget> budgets = budgetRepository.findActiveOn(userId, today);

        long totalLimit = budgets.stream().mapToLong(Budget::getLimitAmount).sum();
        long totalSpent = budgets.stream().mapToLong(Budget::getSpent).sum();
        BudgetProgress total = BudgetProgress.of(totalLimit, totalSpent, today, today);

        return new BudgetSummaryRes(
                totalLimit,
                totalSpent,
                total.remaining(),
                total.ratio(),
                total.status(),
                budgets.size(),
                countWithStatus(budgets, BudgetStatus.OVER_LIMIT, today),
                countWithStatus(budgets, BudgetStatus.NEAR_LIMIT, today));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BudgetAlertRes> alerts(UUID userId) {
        ZoneId zone = timezoneHelper.getCurrentZone();
        LocalDate today = LocalDate.now(zone);
        List<Budget> needingAttention = budgetRepository.findActiveOn(userId, today).stream()
                .filter(budget -> progressOf(budget, today).status().needsAttention())
                .sorted(Comparator.comparing((Budget budget) -> progressOf(budget, today).ratio()).reversed())
                .toList();
        Map<String, CateSumaryRes> categories = categoryHelper.findSummaries(
                needingAttention.stream().map(Budget::getCategoryCode).collect(Collectors.toSet()));

        return needingAttention.stream()
                .map(budget -> toAlert(budget, categories.get(budget.getCategoryCode()), today))
                .toList();
    }

    private BudgetAlertRes toAlert(Budget budget, CateSumaryRes category, LocalDate today) {
        BudgetProgress progress = progressOf(budget, today);
        boolean over = progress.status() == BudgetStatus.OVER_LIMIT;
        return new BudgetAlertRes(
                budget.getId(),
                category != null ? category.name() : UNKNOWN_CATEGORY_NAME,
                over ? SEVERITY_CRITICAL : SEVERITY_ALERT,
                progress.ratio(),
                progress.status(),
                BudgetAlertText.title(progress.status()),
                BudgetAlertText.content(progress));
    }

    private int countWithStatus(List<Budget> budgets, BudgetStatus status, LocalDate today) {
        return (int) budgets.stream().filter(budget -> progressOf(budget, today).status() == status).count();
    }

    private BudgetProgress progressOf(Budget budget, LocalDate today) {
        return BudgetProgress.of(budget.getLimitAmount(), budget.getSpent(), budget.getEndDate(), today);
    }
}
