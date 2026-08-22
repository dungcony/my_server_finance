package com.datn.financeapp.common.security;

import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot tự động đăng ký MỌI bean {@code Filter} (bao gồm {@code @Component} implement
 * {@code OncePerRequestFilter}) thẳng vào servlet container qua {@code ServletContextInitializerBeans}
 * — ĐỘC LẬP với việc {@code SecurityConfig.securityFilterChain} đã thêm chính bean đó vào
 * {@code SecurityFilterChain} bằng {@code addFilterAfter}/{@code addFilterBefore}.
 *
 * Hệ quả: {@link JwtAuthFilter} và {@link RateLimitFilter} chạy HAI LẦN cho mỗi request (một
 * lần do container tự gọi, một lần do Spring Security chain gọi) — với RateLimitFilter nghĩa
 * là mỗi request tiêu tốn 2 token thay vì 1 (Rule 1 — bug đúng nghĩa, phát hiện khi viết test
 * tích hợp Plan 04 gọi liên tiếp /auth/register và bị 429 sớm hơn dự kiến).
 *
 * Tắt auto-registration bằng {@code FilterRegistrationBean.setEnabled(false)} — giữ bean vẫn
 * tồn tại trong context (SecurityConfig vẫn autowire được) nhưng KHÔNG cho container tự gọi nó
 * ngoài đường Spring Security chain.
 */
@Configuration
public class FilterAutoRegistrationConfig {

    @Bean
    public FilterRegistrationBean<Filter> disableAutoRegistrationForJwtAuthFilter(JwtAuthFilter filter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> disableAutoRegistrationForRateLimitFilter(RateLimitFilter filter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
