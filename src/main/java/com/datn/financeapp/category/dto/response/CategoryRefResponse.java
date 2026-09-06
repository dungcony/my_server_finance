package com.datn.financeapp.category.dto.response;

import java.util.UUID;

/**
 * Tham chiếu tới một danh mục kèm biểu tượng — vừa đủ để hiển thị, không lộ cấu trúc bảng.
 *
 * <p>Dùng khi module khác cần "nhắc tên danh mục" trong phản hồi của mình (giao dịch, khoản định
 * kỳ, ngân sách, báo cáo). Trả DTO này thay vì entity {@code Category} để module ngoài không phụ
 * thuộc cấu trúc bảng {@code categories} (quy tắc 11 CLAUDE.md).
 *
 * @param icon có thể {@code null} nếu danh mục không gắn biểu tượng
 * @param parentCategoryId {@code null} nếu đây là danh mục cấp cha. Danh mục tối đa hai cấp nên
 *     không cần đệ quy — bên gọi cần tên cha thì tra thêm một lần bằng id này
 */
public record CategoryRefResponse(
        UUID id,
        String name,
        String type,
        String color,
        IconRefResponse icon,
        UUID parentCategoryId) {}
