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
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String MDC_KEY_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY_REQUEST_ID, requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);

        String method = request.getMethod();
        String fullPath = getFullPath(request);
        String clientIp = ClientIpResolver.resolve(request);

        log.info("--> {} {} [ip={}, reqId={}]", method, fullPath, clientIp, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String userDisplay = resolveUserId(request);

            logResponse(method, fullPath, status, duration, userDisplay, requestId);
            MDC.remove(MDC_KEY_REQUEST_ID);
        }
    }

    private void logResponse(
            String method, String fullPath, int status, long duration, String userDisplay, String requestId) {
        String msg = "<-- {} {} | status={} | time={}ms | user={} | reqId={}";
        if (status >= 500) {
            log.error(msg, method, fullPath, status, duration, userDisplay, requestId);
        } else if (status >= 400) {
            log.warn(msg, method, fullPath, status, duration, userDisplay, requestId);
        } else {
            log.info(msg, method, fullPath, status, duration, userDisplay, requestId);
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

        return "anonymous";
    }
}
