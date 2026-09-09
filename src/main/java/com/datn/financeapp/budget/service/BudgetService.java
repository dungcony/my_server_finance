package com.datn.financeapp.budget.service;

import com.datn.financeapp.budget.dto.request.CreateBudgetRequest;
import com.datn.financeapp.budget.dto.request.UpdateBudgetRequest;
import com.datn.financeapp.budget.dto.response.BudgetAlertResponse;
import com.datn.financeapp.budget.dto.response.BudgetImpactResponse;
import com.datn.financeapp.budget.dto.response.BudgetListItemResponse;
import com.datn.financeapp.budget.dto.response.BudgetSuggestionResponse;
import com.datn.financeapp.budget.dto.response.BudgetSummaryResponse;
import com.datn.financeapp.report.dto.response.ReportHomeResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Public API của module Budget (Quản lý ngân sách chi tiêu, cảnh báo và tự động gia hạn kỳ).
public interface BudgetService {

    List<BudgetListItemResponse> list(UUID userId, Boolean isActive, String periodType);

    BudgetListItemResponse detail(UUID userId, UUID budgetId);

    BudgetSummaryResponse summary(UUID userId);

    List<BudgetAlertResponse> alerts(UUID userId);

    List<ReportHomeResponse.BudgetAttentionItem> attentionItems(UUID userId);

    BudgetSuggestionResponse suggestion(UUID userId, UUID categoryId);

    BudgetListItemResponse create(UUID userId, CreateBudgetRequest req);

    BudgetListItemResponse update(UUID userId, UUID budgetId, UpdateBudgetRequest req);

    void delete(UUID userId, UUID budgetId);

    void renewExpiredBudgets();

    List<BudgetImpactResponse> findImpactedBudgets(
            UUID userId, String type, UUID categoryId, LocalDate date);

    static String formatAmount(long amount) {
        return String.format("%,d ₫", amount).replace(",", ".");
    }

    static LocalDate endOfPeriod(String periodType, LocalDate startDate) {
        return switch (periodType) {
            case "week" -> startDate.plusDays(6);
            case "month" -> startDate.plusMonths(1).minusDays(1);
            case "quarter" -> startDate.plusMonths(3).minusDays(1);
            case "year" -> startDate.plusYears(1).minusDays(1);
            default -> throw new com.datn.financeapp.common.exception.BusinessException(
                    com.datn.financeapp.common.exception.ErrorCode.VALIDATION_ERROR, "Loại kỳ ngân sách không hợp lệ.");
        };
    }
}
