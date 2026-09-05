package com.datn.financeapp.budget.dto.response;

import java.util.UUID;

/**
 * Body {@code GET /budgets/suggestion} (api/05 mục 6, BUDGET-05).
 *
 * <p>{@code suggestedLimit} là {@code null} khi chưa đủ lịch sử — trả NULL tường minh (không bỏ
 * trường) để app biết chắc là "chưa đủ dữ liệu" và ẩn khối gợi ý, thay vì hiện con số bịa.
 */
public record BudgetSuggestionResponse(
        CategorySummary category, Long suggestedLimit, Basis basis, String explanation) {

    public record CategorySummary(UUID id, String name) {}

    public record Basis(Long avg3Months, Long max3Months, Long min3Months, int monthsWithData) {}
}
