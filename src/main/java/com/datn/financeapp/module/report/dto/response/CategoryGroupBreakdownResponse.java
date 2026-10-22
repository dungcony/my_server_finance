package com.datn.financeapp.module.report.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO cho báo cáo phân tích theo nhóm danh mục lớn.
 *
 * @param total  tổng số tiền chi tiêu/thu nhập
 * @param groups danh sách phân tích theo từng nhóm danh mục
 */
public record CategoryGroupBreakdownResponse(Long total, List<GroupItem> groups) {

    /**
     * Thông tin phân tích chi tiết cho một nhóm danh mục.
     *
     * @param categoryGroupId  ID nhóm danh mục
     * @param name             tên nhóm danh mục
     * @param color            màu sắc diện mạo
     * @param amount           tổng số tiền
     * @param ratio            tỷ lệ trên tổng thể
     * @param percentLabel     nhãn phần trăm hiển thị
     * @param transactionCount số lượng giao dịch
     * @param category         danh sách các danh mục thuộc nhóm
     */
    public record GroupItem(
            UUID categoryGroupId,
            String name,
            String color,
            Long amount,
            Double ratio,
            String percentLabel,
            Long transactionCount,
            List<CategoryItem> category) {
    }

    /**
     * Thông tin chi tiết danh mục bên trong nhóm.
     *
     * @param id           ID danh mục
     * @param name         tên danh mục
     * @param amount       tổng số tiền
     * @param ratioInGroup tỷ lệ trong nhóm
     */
    public record CategoryItem(UUID id, String name, Long amount, Double ratioInGroup) {
    }
}
