package com.datn.financeapp.module.report.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO cho báo cáo cơ cấu theo danh mục chi tiết.
 *
 * @param total     tổng số tiền
 * @param avgPerDay trung bình số tiền mỗi ngày
 * @param items     danh sách thông tin cơ cấu theo từng danh mục
 */
public record CategoryBreakdownResponse(Long total, Long avgPerDay, List<Item> items) {

    /**
     * Thông tin chi tiết một danh mục trong báo cáo cơ cấu.
     *
     * @param categoryId        ID danh mục
     * @param name              tên danh mục
     * @param icon              biểu tượng
     * @param color             màu sắc
     * @param amount            số tiền
     * @param ratio             tỷ lệ trên tổng thể
     * @param transactionCount  số lượng giao dịch
     * @param avgPerTransaction trung bình số tiền mỗi giao dịch
     * @param hasChildren       có danh mục con hay không
     * @param childrenDetail    danh sách chi tiết các danh mục con
     */
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
            List<ChildDetail> childrenDetail) {
    }

    /**
     * Thông tin biểu tượng danh mục.
     *
     * @param code     mã biểu tượng
     * @param pathData dữ liệu SVG path
     */
    public record IconRef(String code, String pathData) {
    }

    /**
     * Chi tiết danh mục con bên trong danh mục cha.
     *
     * @param categoryId       ID danh mục con
     * @param name             tên danh mục con
     * @param amount           số tiền
     * @param transactionCount số lượng giao dịch
     */
    public record ChildDetail(UUID categoryId, String name, Long amount, Long transactionCount) {
    }
}
