package com.datn.financeapp.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limit 3 tầng (CORE-04, D-16 đến D-21) — Bucket4j in-memory + Caffeine tự evict.
 *
 * Đặt SAU {@code JwtAuthFilter} trong SecurityFilterChain (D-18) để tầng ai/default đọc được
 * user_id từ SecurityContext (đã được JwtAuthFilter set trước đó).
 *
 * D-19: header X-RateLimit-Limit/Remaining/Reset trả trên MỌI response, không chỉ khi bị chặn.
 * D-20: vượt quota -> 429 kèm mã RATE_LIMIT_EXCEEDED — ghi JSON thô trực tiếp (KHÔNG qua
 * GlobalExceptionHandler vì Filter chạy TRƯỚC DispatcherServlet, không thể ném exception để
 * Controller Advice bắt) nhưng vẫn khớp đúng khung {success,error} chuẩn toàn hệ thống.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;

    private final Cache<String, Bucket> bucketCache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5))
            .maximumSize(100_000)
            .build();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain)
            throws ServletException, IOException {
        RateLimitRule rule = properties.resolve(req.getRequestURI());
        String bucketKey = rule.keyedByIp()
                ? rule.group() + ":" + req.getRemoteAddr()
                : rule.group() + ":" + resolveUserOrIp(req);

        Bucket bucket = bucketCache.get(
                bucketKey,
                k -> Bucket.builder()
                        .addLimit(l -> l.capacity(rule.limit()).refillGreedy(rule.limit(), rule.window()))
                        .build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        res.setHeader("X-RateLimit-Limit", String.valueOf(rule.limit()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        res.setHeader(
                "X-RateLimit-Reset",
                String.valueOf(Instant.now()
                        .plusNanos(probe.getNanosToWaitForRefill())
                        .getEpochSecond()));

        if (!probe.isConsumed()) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter()
                    .write(
                            """
                {"success":false,"error":{"code":"RATE_LIMIT_EXCEEDED","message":"Bạn đã gọi quá nhiều lần, vui lòng thử lại sau."}}
                """);
            return;
        }
        chain.doFilter(req, res);
    }

    private String resolveUserOrIp(HttpServletRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getPrincipal().toString();
        }
        // Chưa đăng nhập -> rơi về IP (hiếm khi xảy ra cho nhóm "default"/"ai" vì các endpoint
        // đó yêu cầu auth, SecurityConfig đã chặn 401 trước khi tới filter này với request hợp lệ).
        return req.getRemoteAddr();
    }
}
