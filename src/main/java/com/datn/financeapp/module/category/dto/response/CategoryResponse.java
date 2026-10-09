package com.datn.financeapp.module.category.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Thông tin phản hồi danh mục.
 *
 * @param id        ID danh mục
 * @param name      Tên danh mục
 * @param type      Loại danh mục
 * @param code      Mã cate
 * @param icon      Biểu tượng danh mục
 * @param color     Mã màu hiển thị (#RRGGBB)
 * @param createdAt Thời điểm tạo danh mục
 * @param isDeleted Đã bị xóa hay chưa
 */
public record CategoryResponse(
        @JsonIgnore UUID id,
        String name,
        String type,
        String code,
        IconResponse icon,
        String color,
        Instant createdAt,
        @JsonIgnore Boolean isDeleted) {
}
