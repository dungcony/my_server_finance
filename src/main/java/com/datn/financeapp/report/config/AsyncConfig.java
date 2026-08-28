package com.datn.financeapp.report.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@code TaskExecutor} riêng cho {@code ExportAsyncRunner} — pool GIỚI HẠN (core=2, max=4,
 * queue=50) để nhiều request export đồng thời không tạo quá nhiều thread (T-04-19, DoS). KHÔNG
 * dùng executor mặc định không giới hạn của {@code @Async}.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "exportTaskExecutor")
    public Executor exportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("export-");
        executor.initialize();
        return executor;
    }
}
