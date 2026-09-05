package com.datn.financeapp.category.dto.response;

import java.time.Instant;
import java.util.UUID;

/** GET /categories/{id} — như CategoryResponse (không kèm children) kèm {@code stats}. */
public record CategoryDetailResponse(
        UUID id,
        String name,
        String type,
        boolean isSystem,
        UUID parentCategoryId,
        CategoryResponse.CategoryGroupSummary categoryGroup,
        CategoryResponse.IconSummary icon,
        String color,
        int sortOrder,
        Instant createdAt,
        Stats stats) {

    /**
     * {@code hasBudget} luôn {@code false} ở Phase 2 — bảng {@code budgets} chưa có service.
     * Phase 4 sẽ nối thật khi module ngân sách tồn tại.
     */
    public record Stats(long transactionCount, long expenseThisMonth, boolean hasBudget) {}
}
