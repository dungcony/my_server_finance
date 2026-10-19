package com.datn.financeapp.module.budget.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.budget.dto.response.BudgetAdminRes;
import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.BudgetProgress;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.mapper.BudgetMapper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.budget.service.BudgetAdminService;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai các chức năng quản trị ngân sách cho Quản trị viên (Admin).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetAdminServiceImpl implements BudgetAdminService {

    private final BudgetRepository budgetRepository;
    private final BudgetCategoryHelper categoryHelper;
    private final BudgetMapper budgetMapper;
    private final UserTimezoneHelper timezoneHelper;

    @Override
    @Transactional(readOnly = true)
    public Page<BudgetAdminRes> list(
            UUID targetUserId,
            Boolean isDeleted,
            Boolean isActive,
            PeriodType periodType,
            Pageable pageable) {

        Page<Budget> page = budgetRepository.searchByAdmin(targetUserId, isDeleted, isActive, periodType, pageable);

        Set<String> categoryCodes = page.getContent().stream()
                .map(Budget::getCategoryCode)
                .collect(Collectors.toSet());
        Map<String, CateSumaryRes> summaries = categoryHelper.findSummaries(categoryCodes);
        LocalDate today = LocalDate.now(timezoneHelper.getCurrentZone());

        return page.map(budget -> {
            BudgetProgress progress = BudgetProgress.of(
                    budget.getLimitAmount(), budget.getSpent(), budget.getEndDate(), today);
            CateSumaryRes category = summaries.get(budget.getCategoryCode());
            return budgetMapper.toAdminRes(budget, progress, category);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetAdminRes detail(UUID budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ngân sách."));

        CateSumaryRes category = categoryHelper.findSummary(budget.getCategoryCode()).orElse(null);
        LocalDate today = LocalDate.now(timezoneHelper.getCurrentZone());
        BudgetProgress progress = BudgetProgress.of(
                budget.getLimitAmount(), budget.getSpent(), budget.getEndDate(), today);

        return budgetMapper.toAdminRes(budget, progress, category);
    }

    @Override
    @Transactional
    public void forceDelete(UUID budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ngân sách."));

        budget.setIsActive(false);
        budget.setIsDeleted(true);
        budgetRepository.save(budget);
        log.info("Admin cưỡng chế xóa ngân sách ID: {}, sở hữu bởi userId: {}", budgetId, budget.getUserId());
    }
}
