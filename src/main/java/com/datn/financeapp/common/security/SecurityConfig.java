package com.datn.financeapp.common.security;

import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityFilterChain STATELESS — không session, xác thực hoàn toàn qua JWT mỗi request.
 * Endpoint /auth/register|login|google|refresh|forgot-password|reset-password công khai, còn lại
 * yêu cầu Bearer token hợp lệ.
 *
 * D-18: RateLimitFilter đặt SAU JwtAuthFilter để tầng ai/default đọc được user_id từ
 * SecurityContext đã được JwtAuthFilter set trước đó trong cùng request.
 *
 * <p>{@code exceptionHandling(authenticationEntryPoint)} BẮT BUỘC: thiếu nó thì request
 * thiếu/hỏng token trả 403 (anonymous → AccessDeniedException) thay vì 401, và
 * {@code GlobalExceptionHandler.handleAuthentication} không bao giờ chạy vì Spring Security
 * xử lý lỗi xác thực ngay trong filter chain, trước DispatcherServlet.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers(
                        org.springframework.boot.autoconfigure.security.servlet.PathRequest.toStaticResources().atCommonLocations()
                )
                .requestMatchers("/", "/login-test.html", "/*.html", "/static/**");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/login-test.html", "/*.html", "/static/**",
                                "/auth/register", "/auth/login", "/auth/refresh",
                                "/auth/forgot-password", "/auth/reset-password",
                                "/auth/verify-email", "/auth/resend-verification",
                                // D3 — người chưa đăng nhập mới bấm nút Google. Danh tính do
                                // chữ ký id_token của Google chứng minh, không phải Bearer token.
                                "/auth/google").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthFilter.class);
        return http.build();
    }

    // D-01 / api/01 "Ghi chú triển khai": bcrypt cost factor >= 12.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
