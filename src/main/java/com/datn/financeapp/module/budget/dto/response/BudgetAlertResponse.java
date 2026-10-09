package com.datn.financeapp.module.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một phần tử của {@code GET /budgets/alerts} (api/05 mục 7, BUDGET-06).
 *
 * @param budgetId     ID ngân sách chi tiêu phát sinh cảnh báo
 * @param category     Tên danh mục chi tiêu của ngân sách
 * @param severity     Mức độ nghiêm trọng của cảnh báo (VD: {@code warning}, {@code critical})
 * @param ratio        Tỷ lệ đã chi tiêu so với hạn mức (VD: 0.85 = 85%)
 * @param percentLabel Nhãn phần trăm hiển thị
 * @param title        Tiêu đề câu cảnh báo
 * @param content      Nội dung chi tiết cảnh báo
 * @param suggestion   Gợi ý hành động xử lý tiếp theo (tùy chọn)
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

    /**
     * Gợi ý xử lý cảnh báo ngân sách.
     *
     * @param content Nội dung câu gợi ý
     * @param action  Loại hành động khuyến nghị
     * @param params  Tham số bổ sung cho hành động
     */
    public record Suggestion(String content, String action, Object params) {
    }
}
