package com.datn.financeapp.module.icon.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

/**
 * Phần tử biểu tượng trong kết quả trả về.
 *
 * @param id          ID biểu tượng (chỉ dùng nội bộ Java, bỏ qua khi serialize JSON)
 * @param code        Mã biểu tượng
 * @param displayName Tên hiển thị biểu tượng
 * @param pathData    Đường dẫn dữ liệu SVG biểu tượng
 */
public record IconResponse(
        @JsonIgnore UUID id,
        String code,
        String displayName,
        String pathData) {
}
