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

    /** Cách dùng thường gặp nhất: mã lỗi kèm câu thông báo mặc định của nó. */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    /**
     * Ghi đè câu thông báo khi ngữ cảnh cần cụ thể hơn câu mặc định.
     *
     * <p>Chỗ dùng nhiều nhất là {@link ErrorCode#NOT_FOUND} — một mã dùng chung cho mọi loại tài
     * nguyên (để không lộ bản ghi có tồn tại hay không), nhưng câu tiếng Việt thì nói rõ "Không
     * tìm thấy ví." hay "Không tìm thấy danh mục." cho chủ sở hữu hợp lệ.
     */
    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /** Thêm {@code detail} có cấu trúc — ví dụ danh sách lỗi theo từng trường. */
    public BusinessException(ErrorCode errorCode, String message, Object detail) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.detail = detail;
    }

}
