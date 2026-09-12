package com.datn.financeapp.common.security;

import com.datn.financeapp.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Trả 401 theo khung {@code {success, error}} khi request chưa xác thực chạm endpoint yêu cầu
 * đăng nhập.
 *
 * <p><b>Vì sao cần lớp này:</b> {@code GlobalExceptionHandler.handleAuthentication} không bao giờ
 * chạy cho lỗi xác thực — Spring Security xử lý ngay trong filter chain, TRƯỚC DispatcherServlet,
 * nên {@code @ExceptionHandler} không nhìn thấy. Không khai báo entry point thì Spring dùng mặc
 * định: anonymous authentication đang bật khiến request thiếu/hỏng token cho ra
 * {@code AccessDeniedException} → <b>403</b>, sai hợp đồng 401 ở api/00-QUY-UOC-CHUNG.md mục 6.
 *
 * <p>Mã lỗi lấy từ request attribute do {@link JwtAuthFilter} đặt, giữ nguyên phân biệt
 * {@code TOKEN_EXPIRED} / {@code TOKEN_INVALID} / {@code UNAUTHENTICATED} — app dựa vào
 * {@code TOKEN_EXPIRED} để quyết định gọi {@code /auth/refresh}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        Object tokenError = request.getAttribute(JwtAuthFilter.ATTR_TOKEN_ERROR);
        String code = tokenError instanceof String s ? s : "UNAUTHENTICATED";
        String message = switch (code) {
            case "TOKEN_EXPIRED" -> "Thẻ truy cập đã hết hạn.";
            case "TOKEN_INVALID" -> "Thẻ truy cập không hợp lệ.";
            case "ACCOUNT_BLOCKED" -> "Tài khoản đã bị khoá. Vui lòng liên hệ hỗ trợ.";
            default -> "Vui lòng đăng nhập để tiếp tục.";
        };

        log.warn("Xác thực thất bại tại {} {}: [{}] {}", request.getMethod(), request.getRequestURI(), code, message);

        var body = new ErrorResponse(false, new ErrorResponse.ErrorBody(code, message));

        int status = "ACCOUNT_BLOCKED".equals(code) ? HttpStatus.FORBIDDEN.value() : HttpStatus.UNAUTHORIZED.value();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
