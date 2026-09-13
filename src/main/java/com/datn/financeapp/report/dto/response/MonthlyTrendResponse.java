package com.datn.financeapp.report.dto.response;

import java.util.List;

// {@code GET /reports/monthly-trend} (api/06-BAO-CAO.md mục 6).
public record MonthlyTrendResponse(
        List<MonthItem> months, Long averageExpense, MonthAmount highestMonth, MonthAmount lowestMonth) {

    public record MonthItem(String month, String label, Long totalIncome, Long totalExpense, Long difference) {}

    public record MonthAmount(String month, Long amount) {}
}
