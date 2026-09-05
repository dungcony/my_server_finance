package com.datn.financeapp.report.dto.response;

import java.util.List;
import java.util.UUID;

/** {@code GET /reports/by-category} (api/06-BAO-CAO.md mục 4). */
public record CategoryBreakdownResponse(Long total, Long avgPerDay, List<Item> items) {

    public record Item(
            UUID categoryId,
            String name,
            IconRef icon,
            String color,
            Long amount,
            Double ratio,
            Long transactionCount,
            Long avgPerTransaction,
            boolean hasChildren,
            List<ChildDetail> childrenDetail) {}

    public record IconRef(String code, String pathData) {}

    /**
     * {@code categoryId == null} nghĩa là giao dịch gán THẲNG vào danh mục cha, không thuộc con
     * nào — luôn xuất hiện khi cha có con, đúng mẫu "Không phân loại ... tiết" của api/06 mục 4.
     */
    public record ChildDetail(UUID categoryId, String name, Long amount, Long transactionCount) {}
}
