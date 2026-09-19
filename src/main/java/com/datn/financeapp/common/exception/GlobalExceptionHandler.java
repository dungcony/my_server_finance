package com.datn.financeapp.common.exception;

import com.datn.financeapp.common.response.ErrorResponse;
import com.datn.financeapp.common.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
// Phải chạy TRƯỚC DefaultHandlerExceptionResolver của Spring MVC — nếu không, resolver mặc định
// giành xử lý MissingServletRequestParameter/MethodArgumentTypeMismatch trước và trả 400 với thân
// rỗng, sau đó Spring Security thấy response chưa commit nên entry point ghi đè thành 401.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(toSnakeCase(fe.getField()), fe.getDefaultMessage()))
                .toList();
        String detail = fields.stream()
                .map(fe -> fe.field() + ": " + fe.message())
                .collect(java.util.stream.Collectors.joining(", "));
        request.setAttribute(com.datn.financeapp.common.logging.RequestLoggingFilter.ATTR_ERROR_DETAIL, "VALIDATION_ERROR: " + detail);
        log.debug("Validation thất bại tại {} {}: {}", request.getMethod(), request.getRequestURI(), fields);
        var body = new ErrorResponse(false,
                new ErrorResponse.ErrorBody("VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.", fields, null));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Thiếu tham số truy vấn bắt buộc, sai kiểu tham số (UUID/ngày/số không parse được), hoặc
     * thân yêu cầu không đọc được — đều là LỖI CỦA CLIENT, phải trả 400 VALIDATION_ERROR theo
     * api/00-QUY-UOC-CHUNG.md mục 6, không phải 500.
     *
     * <p>Thiếu các handler này thì mọi request sai nhẹ đều rơi vào catch-all
     * {@code Exception.class} → 500 INTERNAL_ERROR, khiến app hiện "lỗi máy chủ" cho một lỗi
     * gõ sai tham số và làm nhiễu log lỗi thật.
     */
    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        String field = null;
        if (ex instanceof MissingServletRequestParameterException e) {
            field = e.getParameterName();
        } else if (ex instanceof MethodArgumentTypeMismatchException e) {
            field = e.getName();
        }
        String detail = field != null ? field + ": Thiếu hoặc sai định dạng." : "Dữ liệu gửi lên không đọc được.";
        request.setAttribute(com.datn.financeapp.common.logging.RequestLoggingFilter.ATTR_ERROR_DETAIL, "VALIDATION_ERROR: " + detail);
        log.debug("Yêu cầu không hợp lệ tại {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        List<ErrorResponse.FieldError> fields = field == null
                ? null
                : List.of(new ErrorResponse.FieldError(field, "Thiếu hoặc sai định dạng."));
        var body = new ErrorResponse(false,
                new ErrorResponse.ErrorBody("VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.", fields, null));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Đường dẫn không tồn tại → 404 NOT_FOUND, không phải 500.
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        request.setAttribute(com.datn.financeapp.common.logging.RequestLoggingFilter.ATTR_ERROR_DETAIL, "NOT_FOUND: " + ex.getMessage());
        log.debug("Không tìm thấy tài nguyên tại {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        var body = new ErrorResponse(false,
                new ErrorResponse.ErrorBody("NOT_FOUND", "Không tìm thấy tài nguyên."));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // Sai phương thức HTTP → 405, không phải 500.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        request.setAttribute(com.datn.financeapp.common.logging.RequestLoggingFilter.ATTR_ERROR_DETAIL, "METHOD_NOT_ALLOWED: " + ex.getMessage());
        log.debug("Phương thức không được hỗ trợ tại {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        var body = new ErrorResponse(false,
                new ErrorResponse.ErrorBody("METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ."));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        request.setAttribute(com.datn.financeapp.common.logging.RequestLoggingFilter.ATTR_ERROR_DETAIL, ex.getCode() + ": " + ex.getMessage());
        log.debug("Nghiệp vụ từ chối tại {} {}: [{}] {}", request.getMethod(), request.getRequestURI(), ex.getCode(), ex.getMessage());
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
        request.setAttribute(com.datn.financeapp.common.logging.RequestLoggingFilter.ATTR_ERROR_DETAIL, code + ": " + message);
        log.debug("Xác thực thất bại tại {} {}: [{}] {}", request.getMethod(), request.getRequestURI(), code, message);
        var body = new ErrorResponse(false, new ErrorResponse.ErrorBody(code, message));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        request.setAttribute(com.datn.financeapp.common.logging.RequestLoggingFilter.ATTR_ERROR_DETAIL, "FORBIDDEN: Bạn không có quyền truy cập tài nguyên này.");
        log.debug("Từ chối quyền truy cập tại {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        var body = new ErrorResponse(false,
                new ErrorResponse.ErrorBody("FORBIDDEN", "Bạn không có quyền truy cập tài nguyên này."));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Client ngắt kết nối khi đang stream SSE (F5, đóng tab, broken pipe) hoặc timeout bất đồng bộ.
     * Theo tài liệu docs/sse_exception_handling_spring_boot_3_security_6.md mục 6 & 11:
     * Đây là hành vi bình thường của HTTP client, không phải lỗi máy chủ nên xử lý riêng ở mức DEBUG,
     * dọn dẹp và không trả response body.
     */
    @ExceptionHandler({
            org.apache.catalina.connector.ClientAbortException.class,
            org.springframework.web.context.request.async.AsyncRequestTimeoutException.class,
            org.springframework.web.context.request.async.AsyncRequestNotUsableException.class,
            java.io.IOException.class
    })
    public void handleClientDisconnection(Exception ex, HttpServletRequest request) {
        log.debug("Client đã ngắt kết nối SSE tại {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // 1. Nếu là request stream SSE bị client hủy kết nối (F5, tắt tab)
        if (request.getRequestURI() != null && request.getRequestURI().contains("/admin/logs/stream")) {
            log.debug("Client đóng kết nối stream SSE tại {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
            return null;
        }

        // 2. Nếu là exception do client ngắt kết nối socket ngầm hoặc response đã commit từ trước
        String exName = ex.getClass().getName();
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (exName.contains("ClientAbortException") || exName.contains("AsyncRequestNotUsableException")
                || msg.contains("response is already committed") || msg.contains("Broken pipe")
                || msg.contains("Connection reset")) {
            log.debug("Bỏ qua lỗi client disconnect / response committed tại {} {}: {}", request.getMethod(), request.getRequestURI(), msg);
            return null;
        }

        request.setAttribute(com.datn.financeapp.common.logging.RequestLoggingFilter.ATTR_ERROR_DETAIL, "INTERNAL_SERVER_ERROR" + (msg.isBlank() ? "" : ": " + msg));
        request.setAttribute(com.datn.financeapp.common.logging.RequestLoggingFilter.ATTR_ERROR_EXCEPTION, ex);
        log.debug("Lỗi ngoài dự kiến tại {} {}: {}", request.getMethod(), request.getRequestURI(), msg);

        var body = new ErrorResponse(false,
                new ErrorResponse.ErrorBody("INTERNAL_ERROR", "Đã có lỗi xảy ra, vui lòng thử lại sau."));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Đổi tên trường Java (camelCase) sang tên trường API (snake_case) — api/00-QUY-UOC-CHUNG.md
     * mục 4.4 dùng {@code category_id}, không phải {@code categoryId}. Client so khớp theo tên
     * trường API để tô đỏ đúng ô nhập, nên trả camelCase là sai hợp đồng.
     */
    private static String toSnakeCase(String field) {
        if (field == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(field.length() + 4);
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
