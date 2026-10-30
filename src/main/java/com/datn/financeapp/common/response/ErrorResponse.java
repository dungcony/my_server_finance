package com.datn.financeapp.common.response;

import java.util.List;

/**
 * Khung phản hồi lỗi thống nhất toàn bộ ứng dụng.
 *
 * @param success trạng thái thành công (luôn false)
 * @param error   thân phản hồi chứa chi tiết lỗi
 */
public record ErrorResponse(boolean success, ErrorBody error) {

    /**
     * Chi tiết lỗi trong phản hồi.
     *
     * @param code    mã lỗi
     * @param message thông điệp lỗi
     * @param fields  danh sách lỗi theo trường (nếu validation thất bại)
     * @param detail  dữ liệu bổ sung
     */
    public record ErrorBody(String code, String message, List<FieldError> fields, Object detail) {

        public ErrorBody(String code, String message) {
            this(code, message, null, null);
        }

        public ErrorBody(String code, String message, Object detail) {
            this(code, message, null, detail);
        }
    }

    public record FieldError(String field, String message) {
    }
}
