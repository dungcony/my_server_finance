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

                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException ex) {
                request.setAttribute(ATTR_TOKEN_ERROR, "TOKEN_EXPIRED");
            } catch (JwtException | IllegalArgumentException ex) {
                request.setAttribute(ATTR_TOKEN_ERROR, "TOKEN_INVALID");
            }
        }

        chain.doFilter(request, response);
    }
}
