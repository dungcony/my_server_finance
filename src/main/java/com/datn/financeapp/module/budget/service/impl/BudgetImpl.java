package com.datn.financeapp.module.budget.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.budget.dto.request.CreateBudgetRequest;
import com.datn.financeapp.module.budget.dto.request.UpdateBudgetRequest;
import com.datn.financeapp.module.budget.dto.response.BudgetRes;
import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.helper.BudgetProgress;
import com.datn.financeapp.module.budget.helper.BudgetSpentCalculator;
import com.datn.financeapp.module.budget.mapper.BudgetMapper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.budget.service.BudgetService;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.service.CategoryService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai {@link BudgetService}: thêm, xem, sửa, xoá mềm ngân sách của chính người dùng.
 * <p>
 * Mọi truy vấn kèm {@code userId}; ngân sách của người khác hay không tồn tại đều trả {@code NOT_FOUND}
 * (quy tắc nghiệp vụ 7). {@link #create} dựng số đã chi ban đầu qua {@link BudgetSpentCalculator}.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class BudgetImpl implements BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryService categoryService;
    private final BudgetMapper budgetMapper;
    private final BudgetSpentCalculator spentCalculator;

    @Override
    @Transactional(readOnly = true)
    public List<BudgetRes> list(UUID userId, Boolean isActive, PeriodType periodType) {
        List<Budget> budgets = budgetRepository.search(userId, isActive, periodType);
        Map<UUID, CategoryRes> categories = categoryService.findAllByIds(
                budgets.stream().map(Budget::getCategoryId).collect(Collectors.toSet()));
        return budgets.stream()
                .map(budget -> toRes(budget, categories.get(budget.getCategoryId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetRes detail(UUID userId, UUID budgetId) {
        return toRes(findOwned(userId, budgetId));
    }

    @Override
    @Transactional
    public BudgetRes create(UUID userId, CreateBudgetRequest req) {
        CategoryRes category = categoryService.findActiveById(req.categoryId());

        LocalDate start = req.startDate() != null
                ? req.startDate()
                : req.periodType().startOfCurrent(LocalDate.now());
        LocalDate end = req.periodType().endOf(start);

        if (budgetRepository.existsOverlapping(userId, req.categoryId(), start, end)) {
            throw new BusinessException(ErrorCode.BUDGET_ALREADY_EXISTS);
        }

        Budget budget = Budget.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .categoryId(req.categoryId())
                .spent(spentCalculator.spentInPeriod(userId, req.categoryId(), start, end))
                .limitAmount(req.limitAmount())
                .periodType(req.periodType())
                .startDate(start)
                .endDate(end)
                .autoRenew(req.autoRenew() == null || req.autoRenew())
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        budgetRepository.save(budget);
        return toRes(budget, category);
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
        return toRes(budget);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID budgetId) {
        findOwned(userId, budgetId).setIsActive(false);
    }

    private Budget findOwned(UUID userId, UUID budgetId) {
        return budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ngân sách."));
    }

    // Đổi danh mục hay loại kỳ thực chất là một ngân sách khác: phải xoá rồi tạo lại
    private void rejectChangeOfCategoryOrPeriod(Budget budget, UpdateBudgetRequest req) {
        boolean categoryChanged = req.categoryId() != null && !req.categoryId().equals(budget.getCategoryId());
        boolean periodChanged = req.periodType() != null && req.periodType() != budget.getPeriodType();
        if (categoryChanged || periodChanged) {
            throw new BusinessException(ErrorCode.BUDGET_CATEGORY_NOT_EDITABLE);
        }
    }

    // Bật lại một ngân sách đã tắt có thể làm trùng với ngân sách khác đang hiệu lực
    private void changeActive(Budget budget, boolean active) {
        boolean reactivating = active && !budget.getIsActive();
        if (reactivating && budgetRepository.existsOverlapExceptId(
                budget.getUserId(), budget.getCategoryId(), budget.getStartDate(), budget.getEndDate(),
                budget.getId())) {
            throw new BusinessException(ErrorCode.BUDGET_ALREADY_EXISTS);
        }
        budget.setIsActive(active);
    }

    private BudgetRes toRes(Budget budget) {
        CategoryRes category = categoryService.findAllByIds(Set.of(budget.getCategoryId()))
                .get(budget.getCategoryId());
        return toRes(budget, category);
    }

    private BudgetRes toRes(Budget budget, CategoryRes category) {
        BudgetProgress progress = BudgetProgress.of(
                budget.getLimitAmount(), budget.getSpent(), budget.getEndDate(), LocalDate.now());
        return budgetMapper.toRes(budget, progress, category);
    }
}
