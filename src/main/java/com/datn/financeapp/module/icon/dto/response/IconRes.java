package com.datn.financeapp.module.icon.dto.response;

/**
 * Phần tử biểu tượng trong kết quả trả về.
 *
 * @param code        Mã biểu tượng
 * @param displayName Tên hiển thị biểu tượng
 * @param pathData    Đường dẫn dữ liệu SVG biểu tượng
 */
public record IconRes(
        String code,
        String displayName,
        String pathData) {

    public IconRes() {
        this(null, null, null);
    }
}
