package com.datn.financeapp.module.budget.service.impl;

import com.datn.financeapp.module.budget.dto.response.BudgetAffectedRes;
import com.datn.financeapp.module.budget.events.BudgetAlertEvent;
import com.datn.financeapp.module.budget.helper.BudgetAlertText;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.BudgetProgress;
import com.datn.financeapp.module.budget.helper.BudgetSpent;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.budget.service.BudgetSpendService;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai {@link BudgetSpendService}. Thay đổi spent nguyên tử và bắn cảnh báo khi chạm ngưỡng.
 */
@Service
@RequiredArgsConstructor
public class BudgetSpendImpl implements BudgetSpendService {

    private static final String UNKNOWN_CATEGORY_NAME = "Danh mục đã xoá";

    private final BudgetRepository budgetRepository;
    private final BudgetCategoryHelper categoryHelper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UserTimezoneHelper timezoneHelper;

    @Override
    @Transactional
    public List<BudgetAffectedRes> addExpense(UUID userId, String categoryCode, Instant occurredAt, long amount) {
        ZoneId zone = timezoneHelper.getCurrentZone();
        LocalDate date = timezoneHelper.toLocalDate(occurredAt, zone);
        return addExpense(userId, categoryCode, date, amount);
    }

    @Override
    @Transactional
    public List<BudgetAffectedRes> addExpense(UUID userId, String categoryCode, LocalDate date, long amount) {
        List<BudgetSpent> updated = changeSpent(userId, categoryCode, date, amount);
        if (updated.isEmpty()) {
            return List.of();
        }

        Map<String, CateSumaryRes> categories = categoryHelper.findSummaries(
                updated.stream().map(BudgetSpent::categoryCode).collect(Collectors.toSet()));
        ZoneId zone = timezoneHelper.getCurrentZone();
        LocalDate today = LocalDate.now(zone);

        return updated.stream()
                .map(budget -> toAffected(userId, budget, categories.get(budget.categoryCode()), today))
                .toList();
    }

    @Override
    @Transactional
    public void removeExpense(UUID userId, String categoryCode, Instant occurredAt, long amount) {
        ZoneId zone = timezoneHelper.getCurrentZone();
        LocalDate date = timezoneHelper.toLocalDate(occurredAt, zone);
        removeExpense(userId, categoryCode, date, amount);
    }

    @Override
    @Transactional
    public void removeExpense(UUID userId, String categoryCode, LocalDate date, long amount) {
        changeSpent(userId, categoryCode, date, -amount);
    }

    private List<BudgetSpent> changeSpent(UUID userId, String categoryCode, LocalDate date, long delta) {
        if (categoryCode == null || categoryCode.isBlank() || delta == 0 || date == null) {
            return List.of();
        }

        List<String> categoryCodes = categoryHelper.findAncestorCodes(categoryCode);
        if (categoryCodes.isEmpty()) {
            return List.of();
        }
        List<UUID> budgetIds = budgetRepository.findActiveByCategoryCodes(userId, categoryCodes, date).stream()
                .map(BudgetSpent::id)
                .toList();
        if (budgetIds.isEmpty()) {
            return List.of();
        }

        budgetRepository.addSpent(budgetIds, delta);
        return budgetRepository.findSpentByIds(budgetIds);
    }

    private BudgetAffectedRes toAffected(UUID userId, BudgetSpent budget, CateSumaryRes category, LocalDate today) {
        BudgetProgress progress = BudgetProgress.of(budget.limitAmount(), budget.spent(), budget.endDate(), today);
        boolean needsAttention = progress.status().needsAttention();

        String alert = needsAttention ? BudgetAlertText.content(progress) : null;
        if (needsAttention) {
            applicationEventPublisher.publishEvent(new BudgetAlertEvent(
                    userId,
                    BudgetAlertText.title(progress.status()),
                    alert,
                    budget.id()
            ));
        }
        String categoryName = category != null ? category.name() : UNKNOWN_CATEGORY_NAME;
        return new BudgetAffectedRes(
                budget.id(), categoryName, budget.limitAmount(), budget.spent(), progress.ratio(), progress.status(),
                alert);
    }
}
