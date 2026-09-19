package com.datn.financeapp.common.logging;

import com.datn.financeapp.common.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter ghi log HTTP access cho toàn bộ API request tới server.
 *
 * <p>Tính năng:
 * <ul>
 *   <li>Tạo hoặc nhận diện header {@code X-Request-Id} và đưa vào {@link MDC} để trace request xuyên suốt.</li>
 *   <li>Ghi log request vào: HTTP method, URI, query params, client IP.</li>
 *   <li>Ghi log response ra: HTTP status code, thời gian xử lý (ms), userId đã xác thực (nếu có).</li>
 *   <li>Phân loại log level: 2xx/3xx -> INFO, 4xx -> WARN, 5xx -> ERROR.</li>
 * </ul>
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "authenticated.userId";
    public static final String ATTR_ERROR_DETAIL = "request.errorDetail";
    public static final String ATTR_ERROR_EXCEPTION = "request.errorException";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String MDC_KEY_REQUEST_ID = "requestId";

    private static final String[] STATIC_EXTENSIONS = {
            ".css", ".js", ".html", ".ico", ".png", ".jpg", ".jpeg", ".svg", ".gif",
            ".woff", ".woff2", ".ttf", ".eot", ".map"
    };

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        if (isStaticAsset(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();

        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String method = request.getMethod();
        MDC.put(MDC_KEY_REQUEST_ID, requestId);
        MDC.put("api", method + " " + request.getRequestURI());
        response.setHeader(HEADER_REQUEST_ID, requestId);
        String fullPath = getFullPath(request);

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String userDisplay = resolveUserId(request);
            String errorDetail = (String) request.getAttribute(ATTR_ERROR_DETAIL);
            Throwable ex = (Throwable) request.getAttribute(ATTR_ERROR_EXCEPTION);

            logResponse(method, fullPath, status, duration, userDisplay, requestId, errorDetail, ex);
            // Dọn sạch toàn bộ MDC (không chỉ requestId) vì filter này bọc ngoài cùng
            // (HIGHEST_PRECEDENCE) toàn bộ chain, kể cả JwtAuthFilter phía sau có thể đã
            // put thêm "userId". Thread của Tomcat được tái sử dụng giữa các request, nếu
            // không clear hết thì request kế tiếp trên cùng thread có thể lộ userId của
            // request trước.
            MDC.clear();
        }
    }

    private boolean isStaticAsset(String uri) {
        if (uri == null) return false;
        String lower = uri.toLowerCase();
        for (String ext : STATIC_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return lower.contains("/static/") || lower.contains("/pages/") || lower.contains("/css/")
                || lower.contains("/js/") || lower.contains("/sass/") || lower.equals("/favicon.ico");
    }

    private void logResponse(
            String method, String fullPath, int status, long duration,
            String userDisplay, String requestId, String errorDetail, Throwable ex) {
        String detail = (errorDetail != null && !errorDetail.isBlank()) ? errorDetail : "-";
        String msg = "<-- {} {} | status={} | time={}ms | user={} | reqId={} | {}";
        if (status >= 500) {
            if (ex != null) {
                log.error(msg, method, fullPath, status, duration, userDisplay, requestId, detail, ex);
            } else {
                log.error(msg, method, fullPath, status, duration, userDisplay, requestId, detail);
            }
        } else if (status >= 400) {
            log.warn(msg, method, fullPath, status, duration, userDisplay, requestId, detail);
        } else {
            log.info(msg, method, fullPath, status, duration, userDisplay, requestId, detail);
        }
    }

    private String getFullPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return (query != null && !query.isBlank()) ? uri + "?" + query : uri;
    }

    private String resolveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(ATTR_USER_ID);
        if (attr instanceof String userId && !userId.isBlank()) {
            return userId;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }

        return "anon";
    }
}
