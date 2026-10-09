package com.datn.financeapp.module.budget.dto.response;

import java.util.UUID;

/**
 * Dữ liệu gợi ý hạn mức ngân sách chi tiêu.
 *
 * @param category       Thông tin tóm tắt danh mục xin gợi ý
 * @param suggestedLimit Hạn mức chi tiêu gợi ý (Đơn vị: VND; null nếu chưa đủ dữ liệu)
 * @param basis          Cơ sở dữ liệu tính toán hạn mức gợi ý
 * @param explanation    Lời giải thích/diễn giải cho gợi ý
 */
public record BudgetSuggestionResponse(
        CategorySummary category, Long suggestedLimit, Basis basis, String explanation) {

    /**
     * Tóm tắt danh mục chi tiêu.
     *
     * @param id   ID danh mục
     * @param name Tên danh mục
     */
    public record CategorySummary(UUID id, String name) {
    }

    /**
     * Cơ sở dữ liệu tính toán gợi ý hạn mức.
     *
     * @param avg3Months     Mức chi trung bình các kỳ đã kết thúc (Đơn vị: VND)
     * @param max3Months     Mức chi cao nhất trong các kỳ (Đơn vị: VND)
     * @param min3Months     Mức chi thấp nhất trong các kỳ (Đơn vị: VND)
     * @param monthsWithData Số lượng kỳ ngân sách có dữ liệu lịch sử
     */
    public record Basis(Long avg3Months, Long max3Months, Long min3Months, int monthsWithData) {
    }
}
