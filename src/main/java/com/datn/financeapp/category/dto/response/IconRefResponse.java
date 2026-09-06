package com.datn.financeapp.category.dto.response;

/**
 * Tham chiếu tối thiểu tới một biểu tượng — mã và dữ liệu đường vẽ SVG.
 *
 * <p>Dùng khi module khác cần hiển thị biểu tượng kèm bản ghi của mình (mục tiêu, khoản định kỳ,
 * ngân sách). Trả DTO này thay vì entity {@code Icon} để module ngoài không phụ thuộc vào cấu
 * trúc bảng {@code icons} (quy tắc 11 CLAUDE.md).
 */
public record IconRefResponse(String code, String pathData) {}
