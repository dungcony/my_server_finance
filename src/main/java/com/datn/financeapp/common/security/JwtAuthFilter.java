package com.datn.financeapp.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Đọc header Authorization: Bearer <token>, verify bằng JwtService, set SecurityContext
 * nếu hợp lệ. Nếu thiếu header hoặc token không hợp lệ, KHÔNG set SecurityContext, để
 * chain.doFilter tiếp tục — Spring Security tự trả 401 ở entry point cho endpoint yêu
 * cầu auth (qua GlobalExceptionHandler.handleAuthentication).
 *
 * Bắt riêng ExpiredJwtException để set request attribute phân biệt TOKEN_EXPIRED vs
 * TOKEN_INVALID cho tầng sau (entry point/controller) dùng khi cần.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_TOKEN_ERROR = "jwt.tokenError";

    private final JwtService jwtService;
    private final BlacklistedUserRepository blacklistedUserRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseAndValidate(token);
                String userId = claims.getSubject();

                if (blacklistedUserRepository.isBlacklisted(userId)) {
                    request.setAttribute(ATTR_TOKEN_ERROR, "ACCOUNT_BLOCKED");
                    chain.doFilter(request, response);
                    return;
                }

                java.util.List<org.springframework.security.core.GrantedAuthority> grantedAuthorities = Collections.emptyList();
                Object authoritiesClaim = claims.get("authorities");
                if (authoritiesClaim instanceof java.util.List<?> list) {
                    grantedAuthorities = list.stream()
                            .filter(item -> item instanceof String)
                            .map(item -> new org.springframework.security.core.authority.SimpleGrantedAuthority((String) item))
                            .collect(java.util.stream.Collectors.toList());
                }

                Integer topRoleLevel = claims.get("roles_level_top", Integer.class);

                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, grantedAuthorities);
                authentication.setDetails(topRoleLevel);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(com.datn.financeapp.common.logging.RequestLoggingFilter.ATTR_USER_ID, userId);
            } catch (ExpiredJwtException ex) {
                request.setAttribute(ATTR_TOKEN_ERROR, "TOKEN_EXPIRED");
            } catch (JwtException | IllegalArgumentException ex) {
                request.setAttribute(ATTR_TOKEN_ERROR, "TOKEN_INVALID");
            }
        }

        chain.doFilter(request, response);
    }
}
