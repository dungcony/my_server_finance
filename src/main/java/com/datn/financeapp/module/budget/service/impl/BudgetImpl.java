package com.datn.financeapp.module.budget.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.budget.dto.request.CreateBudgetRequest;
import com.datn.financeapp.module.budget.dto.request.UpdateBudgetRequest;
import com.datn.financeapp.module.budget.dto.response.BudgetRes;
import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.BudgetProgress;
import com.datn.financeapp.module.budget.helper.BudgetSpentCalculator;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.mapper.BudgetMapper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.budget.service.BudgetService;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Triển khai {@link BudgetService}. Quản lý CRUD ngân sách, kiểm tra hợp lệ và tính toán ban đầu.
 */
@Service
@RequiredArgsConstructor
public class BudgetImpl implements BudgetService {

    private final BudgetRepository budgetRepository;
    private final BudgetCategoryHelper categoryHelper;
    private final BudgetMapper budgetMapper;
    private final BudgetSpentCalculator spentCalculator;
    private final UserTimezoneHelper timezoneHelper;

    @Override
    @Transactional(readOnly = true)
    public List<BudgetRes> list(UUID userId, Boolean isActive, PeriodType periodType) {
        List<Budget> budgets = budgetRepository.search(userId, isActive, periodType);
        Set<String> categoryCodes = budgets.stream()
                .map(Budget::getCategoryCode)
                .collect(Collectors.toSet());
        Map<String, CateSumaryRes> categories = categoryHelper.findSummaries(categoryCodes);
        ZoneId zone = timezoneHelper.getCurrentZone();
        LocalDate today = LocalDate.now(zone);
        return budgets.stream()
                .map(budget -> toRes(budget, categories.get(budget.getCategoryCode()), today))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetRes detail(UUID userId, UUID budgetId) {
        ZoneId zone = timezoneHelper.getCurrentZone();
        return toRes(findOwned(userId, budgetId), LocalDate.now(zone));
    }

    @Override
    @Transactional
    public BudgetRes create(UUID userId, CreateBudgetRequest req) {
        CateSumaryRes category = categoryHelper.findSummary(req.categoryCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh mục không tồn tại hoặc đã bị xóa."));

        ZoneId userZone = timezoneHelper.getCurrentZone();
        LocalDate today = LocalDate.now(userZone);
        LocalDate start = req.startDate() != null
                ? req.startDate()
                : req.periodType().startOfCurrent(today);
        LocalDate end = req.periodType().endOf(start);

        if (budgetRepository.existsOverlapping(userId, req.categoryCode(), start, end)) {
            throw new BusinessException(ErrorCode.BUDGET_ALREADY_EXISTS);
        }

        Budget budget = Budget.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .categoryCode(req.categoryCode())
                .spent(spentCalculator.spentInPeriod(userId, req.categoryCode(), start, end, userZone))
                .limitAmount(req.limitAmount())
                .periodType(req.periodType())
                .startDate(start)
                .endDate(end)
                .autoRenew(req.autoRenew() == null || req.autoRenew())
                .isActive(true)
                .build();
        budgetRepository.save(budget);
        return toRes(budget, category, today);
    }

    @Override
    @Transactional
    public BudgetRes update(UUID userId, UUID budgetId, UpdateBudgetRequest req) {
        Budget budget = findOwned(userId, budgetId);
        rejectChangeOfCategoryOrPeriod(budget, req);

        if (req.limitAmount() != null) {
            budget.setLimitAmount(req.limitAmount());
        }
        if (req.autoRenew() != null) {
            budget.setAutoRenew(req.autoRenew());
        }
        if (req.isActive() != null) {
            changeActive(budget, req.isActive());
        }
        ZoneId zone = timezoneHelper.getCurrentZone();
        return toRes(budget, LocalDate.now(zone));
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID budgetId) {
        Budget budget = findOwned(userId, budgetId);
        budget.setIsActive(false);
        budget.setIsDeleted(true);
    }

    private Budget findOwned(UUID userId, UUID budgetId) {
        return budgetRepository.findByIdAndUserIdAndIsDeletedFalse(budgetId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ngân sách."));
    }

    private void rejectChangeOfCategoryOrPeriod(Budget budget, UpdateBudgetRequest req) {
        boolean categoryChanged = req.categoryCode() != null && !req.categoryCode().equals(budget.getCategoryCode());
        boolean periodChanged = req.periodType() != null && req.periodType() != budget.getPeriodType();
        if (categoryChanged || periodChanged) {
            throw new BusinessException(ErrorCode.BUDGET_CATEGORY_NOT_EDITABLE);
        }
    }

    private void changeActive(Budget budget, boolean active) {
        boolean reactivating = active && !budget.getIsActive();
        if (reactivating && budgetRepository.existsOverlapExceptId(
                budget.getUserId(), budget.getCategoryCode(), budget.getStartDate(), budget.getEndDate(),
                budget.getId())) {
            throw new BusinessException(ErrorCode.BUDGET_ALREADY_EXISTS);
        }
        budget.setIsActive(active);
    }

    private BudgetRes toRes(Budget budget, LocalDate today) {
        CateSumaryRes category = categoryHelper.findSummary(budget.getCategoryCode()).orElse(null);
        return toRes(budget, category, today);
    }

    private BudgetRes toRes(Budget budget, CateSumaryRes category, LocalDate today) {
        BudgetProgress progress = BudgetProgress.of(
                budget.getLimitAmount(), budget.getSpent(), budget.getEndDate(), today);
        return budgetMapper.toRes(budget, progress, category);
    }
}
