package com.datn.financeapp.category.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Phần tử GET /categories — record đệ quy, {@code children} chỉ điền khi trả dạng cây. */
public record CategoryResponse(
        UUID id,
        String name,
        String type,
        boolean isSystem,
        UUID parentCategoryId,
        CategoryGroupSummary categoryGroup,
        IconSummary icon,
        String color,
        int sortOrder,
        Instant createdAt,
        List<CategoryResponse> children) {

    public record CategoryGroupSummary(UUID id, String name, String color) {}

    public record IconSummary(UUID id, String code, String pathData) {}
}
