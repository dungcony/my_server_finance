package com.datn.financeapp.module.notification.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@code TaskExecutor} riêng cho các listener gửi thông báo chạy {@code @Async} sau khi transaction
 * commit. Pool GIỚI HẠN (core=2, max=4, queue=100) để một đợt giao dịch nhóm lớn không tạo quá nhiều
 * thread. KHÔNG dùng executor mặc định không giới hạn của {@code @Async}.
 */
@Configuration
public class NotificationAsyncConfig {

    @Bean(name = "notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-");
        executor.initialize();
        return executor;
    }
}
