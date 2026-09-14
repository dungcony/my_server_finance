package com.datn.financeapp.category.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phần tử GET /categories — record đệ quy, {@code children} chỉ điền khi trả dạng cây.
 *
 * <p>{@code isEnabled} chỉ có giá trị ở {@code GET /wallets/{id}/categories} (api/03 mục 9); ở
 * {@code GET /categories} thường nó là {@code null} và bị loại khỏi JSON, vì "bật/tắt" là khái
 * niệm gắn với một ví cụ thể chứ không phải thuộc tính của danh mục.
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
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
        Boolean isEnabled,
        List<CategoryResponse> children) {

    /** Bản sao kèm cờ bật/tắt và danh sách con đã phủ cờ — dùng khi trả cây theo ví. */
    public CategoryResponse withEnabled(Boolean enabled, List<CategoryResponse> children) {
        return new CategoryResponse(
                id, name, type, isSystem, parentCategoryId, categoryGroup, icon, color, sortOrder,
                createdAt, enabled, children);
    }

    public record CategoryGroupSummary(UUID id, String name, String color) {}

    public record IconSummary(UUID id, String code, String pathData) {}
}
