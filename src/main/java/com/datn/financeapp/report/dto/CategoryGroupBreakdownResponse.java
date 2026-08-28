package com.datn.financeapp.report.dto;

import java.util.List;
import java.util.UUID;

/** {@code GET /reports/by-category-group} (api/06-BAO-CAO.md mục 3) — phục vụ biểu đồ tròn. */
public record CategoryGroupBreakdownResponse(Long total, List<GroupItem> groups) {

    public record GroupItem(
            UUID categoryGroupId,
            String name,
            String color,
            Long amount,
            Double ratio,
            String percentLabel,
            Long transactionCount,
            List<CategoryItem> category) {}

    /** Số tiền của từng danh mục cha bên trong nhóm — ĐÃ cộng gộp con (api/06 mục 3). */
    public record CategoryItem(UUID id, String name, Long amount, Double ratioInGroup) {}
}
