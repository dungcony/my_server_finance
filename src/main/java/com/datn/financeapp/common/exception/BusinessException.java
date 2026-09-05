package com.datn.financeapp.common.exception;

import lombok.Getter;

/**
 * Exception nghiệp vụ tổng quát mang theo mã lỗi + HTTP status xác định.
 * GlobalExceptionHandler chuyển thẳng thành {success:false, error:{code, message, detail}}.
 *
 * <p><b>Luôn dùng các constructor nhận {@link ErrorCode}.</b> Constructor nhận {@code String} chỉ
 * còn tồn tại để chuyển dần từng module sang enum mà không phải sửa 147 chỗ trong một commit —
 * nó sẽ bị gỡ ở bước E5 (xem {@code prd/02-CHUAN-HOA-KIEN-TRUC-BACKEND/STATE.md}). Đừng viết chỗ
 * gọi mới bằng nó.
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

    /**
     * @deprecated Dùng {@link #BusinessException(ErrorCode)} hoặc
     *     {@link #BusinessException(ErrorCode, String)}. Constructor này không kiểm được chính tả
     *     mã lỗi lúc biên dịch — đúng vấn đề mà {@link ErrorCode} sinh ra để giải quyết. Sẽ gỡ ở
     *     bước E5.
     */
    @Deprecated
    public BusinessException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    /**
     * @deprecated Dùng {@link #BusinessException(ErrorCode, String, Object)}. Sẽ gỡ ở bước E5.
     */
    @Deprecated
    public BusinessException(String code, int httpStatus, String message, Object detail) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.detail = detail;
    }
}
