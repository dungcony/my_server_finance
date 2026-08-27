package com.datn.financeapp.budget.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Body {@code GET /budgets/summary} (api/05 mục 3) — phần đầu màn Ngân sách. */
public record BudgetSummaryResponse(
        Period period,
        Long totalLimit,
        Long totalSpent,
        Long totalRemaining,
        BigDecimal ratio,
        String status,
        int budgetCount,
        int overLimitCount,
        int nearLimitCount) {

    /** Kỳ đại diện, suy từ ngân sách đang hiệu lực; NULL khi người dùng chưa có ngân sách nào. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Period(
            String periodType, String label, LocalDate startDate, LocalDate endDate, Integer daysRemaining) {}
}
