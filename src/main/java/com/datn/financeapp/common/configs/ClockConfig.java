package com.datn.financeapp.common.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Cấu hình {@link Clock} dùng chung toàn hệ thống, mặc định theo UTC.
 * Cho phép mock hoặc tua thời gian trong test.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
