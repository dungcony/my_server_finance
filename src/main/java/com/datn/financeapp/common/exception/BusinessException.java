package com.datn.financeapp.common.exception;

import lombok.Getter;

/**
 * Exception nghiệp vụ tổng quát mang theo mã lỗi + HTTP status xác định.
 * GlobalExceptionHandler chuyển thẳng thành {success:false, error:{code, message, detail}}.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final Object detail;

    public BusinessException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    public BusinessException(String code, int httpStatus, String message, Object detail) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.detail = detail;
    }
}
