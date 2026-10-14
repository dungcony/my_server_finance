package com.datn.financeapp.module.category.dto.response;

import com.datn.financeapp.module.icon.dto.response.IconResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin phản hồi danh mục.
 *
 * @param id          ID danh mục (không xuất ra JSON, client dùng {@code code})
 * @param name        Tên danh mục
 * @param code        Mã danh mục
 * @param description Mô tả danh mục
 * @param parentCode  Mã danh mục cha ({@code null} nếu là danh mục gốc)
 * @param icon        Biểu tượng danh mục
 * @param color       Mã màu hiển thị (#RRGGBB)
 * @param level       Độ sâu trong cây (gốc = 0)
 * @param isLeaf      Là danh mục lá (không còn con) hay không
 * @param createdAt   Thời điểm tạo danh mục
 * @param isDeleted   Đã bị xóa hay chưa (không xuất ra JSON)
 */
public record CategoryResponse(
        @JsonIgnore UUID id,
        String name,
        String code,
        String description,
        String parentCode,
        IconResponse icon,
        String color,
        Integer level,
        Boolean isLeaf,
        Instant createdAt,
        @JsonIgnore Boolean isDeleted) {
}
