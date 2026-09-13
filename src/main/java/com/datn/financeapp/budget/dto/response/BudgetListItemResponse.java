package com.datn.financeapp.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Một phần tử của {@code GET /budgets} và toàn bộ body {@code GET /budgets/{id}} (api/05 mục 1).
public record BudgetListItemResponse(
        UUID id,
        CategorySummary category,
        WalletSummary wallet,
        Long limitAmount,
        Long spentAmount,
        Long remaining,
        BigDecimal ratio,
        Integer percentLabel,
        String status,
        String statusColor,
        String periodType,
        LocalDate startDate,
        LocalDate endDate,
        Integer daysRemaining,
        Boolean autoRenew,
        boolean isShared,
        Alert alert) {

    public record CategorySummary(UUID id, String name, IconSummary icon, String color) {}

    public record IconSummary(String code, String pathData) {}

    public record WalletSummary(UUID id, String name) {}

    /**
     * Khối cảnh báo hiển thị kèm ngân sách. {@code projectedDepletionDate} chỉ khác NULL khi ngày
     * dự báo rơi TRƯỚC {@code end_date} (api/05 mục 2.3) — {@code NON_NULL} để Jackson bỏ hẳn
     * trường thay vì trả {@code null} tường minh.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Alert(String title, String content, LocalDate projectedDepletionDate) {}
}
