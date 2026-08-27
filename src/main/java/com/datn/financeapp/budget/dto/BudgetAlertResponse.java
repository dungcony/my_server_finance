package com.datn.financeapp.budget.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một phần tử của {@code GET /budgets/alerts} (api/05 mục 7, BUDGET-06).
 *
 * <p>Đây là nguồn sự thật cho TRẠNG THÁI HIỆN TẠI — tính tại chỗ từ {@code v_budget_progress} mỗi
 * lần gọi. Khác hẳn bảng {@code notifications} (lịch sử tại thời điểm vượt ngưỡng, không tự sửa
 * lại khi người dùng xoá giao dịch sau đó). Hai nguồn cố ý khác nhau, đừng cố đồng bộ (D-41).
 */
public record BudgetAlertResponse(
        UUID budgetId,
        String category,
        String severity,
        BigDecimal ratio,
        String percentLabel,
        String title,
        String content,
        @JsonInclude(JsonInclude.Include.NON_NULL) Suggestion suggestion) {

    public record Suggestion(String content, String action, Object params) {}
}
