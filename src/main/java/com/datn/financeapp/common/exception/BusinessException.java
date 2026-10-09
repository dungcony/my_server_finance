package com.datn.financeapp.common.exception;

import lombok.Getter;

/**
 * Exception nghiệp vụ tổng quát mang theo mã lỗi + HTTP status xác định.
 * GlobalExceptionHandler chuyển thẳng thành {success:false, error:{code, message, detail}}.
 *
 * <p><b>Mã lỗi chỉ nhận qua {@link ErrorCode}.</b> Constructor nhận {@code String} đã được gỡ ở
 * bước E5 — chính nó là thứ cho phép gõ sai mã mà build vẫn qua. Muốn thêm mã lỗi mới thì khai
 * một hằng số trong {@link ErrorCode}, đừng tìm cách truyền chuỗi vào đây.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final Object detail;

    /**
     * Khởi tạo ngoại lệ nghiệp vụ với mã lỗi mặc định.
     *
     * @param errorCode mã lỗi nghiệp vụ
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    /**
     * Khởi tạo ngoại lệ nghiệp vụ với câu thông báo tùy chỉnh.
     *
     * @param errorCode mã lỗi nghiệp vụ
     * @param message   câu thông báo lỗi
     */
    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * Khởi tạo ngoại lệ nghiệp vụ đầy đủ tham số.
     *
     * @param errorCode mã lỗi nghiệp vụ
     * @param message   câu thông báo lỗi
     * @param detail    thông tin chi tiết bổ sung
     */
    public BusinessException(ErrorCode errorCode, String message, Object detail) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.detail = detail;
    }

}
