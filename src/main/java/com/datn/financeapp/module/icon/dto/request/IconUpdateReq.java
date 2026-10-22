package com.datn.financeapp.module.icon.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Dữ liệu yêu cầu cập nhật biểu tượng. Các trường null được giữ nguyên giá trị cũ.
 *
 * @param displayName Tên hiển thị mới (tùy chọn)
 * @param pathData    Dữ liệu đường vẽ SVG mới (tùy chọn)
 */
public record IconUpdateReq(
        @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự.")
        String displayName,
        String code,
        String pathData) {
}
