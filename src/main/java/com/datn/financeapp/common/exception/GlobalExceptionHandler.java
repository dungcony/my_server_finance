package com.datn.financeapp.common.exception;

import com.datn.financeapp.common.response.ErrorResponse;
import com.datn.financeapp.common.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Gom mọi exception thành khung phản hồi thống nhất {success:false, error:{...}}
 * theo api/00-QUY-UOC-CHUNG.md mục 4.3/4.4/6.
 *
 * Ranh giới rò rỉ thông tin nội bộ (T-02-02): catch-all Exception.class KHÔNG đưa
 * message/stack trace gốc ra response, chỉ log SLF4J ở server-side.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        var body = new ErrorResponse(false,
                new ErrorResponse.ErrorBody("VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.", fields, null));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        var body = new ErrorResponse(false,
                new ErrorResponse.ErrorBody(ex.getCode(), ex.getMessage(), ex.getDetail()));
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        Object tokenError = request.getAttribute(JwtAuthFilter.ATTR_TOKEN_ERROR);
        String code = tokenError instanceof String s ? s : "UNAUTHENTICATED";
        String message = "TOKEN_EXPIRED".equals(code)
                ? "Thẻ truy cập đã hết hạn."
                : "TOKEN_INVALID".equals(code)
                        ? "Thẻ truy cập không hợp lệ."
                        : "Vui lòng đăng nhập để tiếp tục.";
        var body = new ErrorResponse(false, new ErrorResponse.ErrorBody(code, message));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        var body = new ErrorResponse(false,
                new ErrorResponse.ErrorBody("FORBIDDEN", "Bạn không có quyền truy cập tài nguyên này."));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Lỗi ngoài dự kiến tại {} {}", request.getMethod(), request.getRequestURI(), ex);
        var body = new ErrorResponse(false,
                new ErrorResponse.ErrorBody("INTERNAL_ERROR", "Đã có lỗi xảy ra, vui lòng thử lại sau."));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
