package com.datn.financeapp.common.response;

import java.util.List;

// Khung phản hồi lỗi thống nhất, theo api/00-QUY-UOC-CHUNG.md mục 4.3/4.4.
public record ErrorResponse(boolean success, ErrorBody error) {

    public record ErrorBody(String code, String message, List<FieldError> fields, Object detail) {

        public ErrorBody(String code, String message) {
            this(code, message, null, null);
        }

        public ErrorBody(String code, String message, Object detail) {
            this(code, message, null, detail);
        }
    }

    public record FieldError(String field, String message) {}
}
