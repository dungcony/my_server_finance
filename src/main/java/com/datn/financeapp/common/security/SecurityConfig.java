package com.datn.financeapp.common.security;

import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityFilterChain STATELESS — không session, xác thực hoàn toàn qua JWT mỗi request.
 * Endpoint /auth/register|login|google|refresh|forgot-password|reset-password công khai, còn lại
 * yêu cầu Bearer token hợp lệ.
 * <p>
 * D-18: RateLimitFilter đặt SAU JwtAuthFilter để tầng ai/default đọc được user_id từ
 * SecurityContext đã được JwtAuthFilter set trước đó trong cùng request.
 * <p>
 * {@code exceptionHandling(authenticationEntryPoint)} BẮT BUỘC: thiếu nó thì request
 * thiếu/hỏng token trả 403 (anonymous → AccessDeniedException) thay vì 401, và
 * {@code GlobalExceptionHandler.handleAuthentication} không bao giờ chạy vì Spring Security
 * xử lý lỗi xác thực ngay trong filter chain, trước DispatcherServlet.
 */
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                PathRequest.toStaticResources().atCommonLocations()
                        ).permitAll()
                        .requestMatchers(
                                "/", "/index.html", "/login-test.html", "/*.html",
                                "/static/**", "/pages/**", "/css/**", "/js/**", "/sass/**",
                                "/auth/register", "/auth/login", "/auth/refresh",
                                "/auth/forgot-password", "/auth/reset-password",
                                "/auth/verify-email", "/auth/resend-verification",
                                "/auth/google")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthFilter.class)
                .cors(Customizer.withDefaults());
        return http.build();
    }


    // 2. Thêm Bean corsConfigurationSource vào SecurityConfig:
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Cho phép các domain frontend khi dev (hoặc lấy từ file cấu hình application.yml)
        configuration.setAllowedOriginPatterns(List.of(
             "*"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization")); // Cho phép frontend đọc header Authorization nếu cần
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
