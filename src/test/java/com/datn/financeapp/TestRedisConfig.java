package com.datn.financeapp;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Cấu hình Redis dùng cho môi trường Test.
 * Nếu cổng 6379 trên localhost đã mở (ví dụ developer chạy docker-compose up redis), dùng luôn localhost:6379.
 * Nếu chưa có Redis chạy, tự động bật 1 container redis:7-alpine qua Testcontainers
 * và truyền thông tin host/port vào System property để Spring Boot tự động kết nối.
 */
@Slf4j
@Configuration
public class TestRedisConfig {

    static {
        boolean localRedisAvailable = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 6379), 300);
            localRedisAvailable = true;
            log.info("TestRedisConfig: Phát hiện Redis cục bộ đang chạy tại localhost:6379. Sử dụng Redis cục bộ.");
        } catch (Exception ignored) {
            // Không có Redis cục bộ chạy
        }

        if (!localRedisAvailable) {
            try {
                log.info("TestRedisConfig: Khởi động Redis Testcontainer (redis:7-alpine)...");
                GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379);
                redisContainer.start();
                System.setProperty("spring.data.redis.host", redisContainer.getHost());
                System.setProperty("spring.data.redis.port", String.valueOf(redisContainer.getFirstMappedPort()));
                log.info("TestRedisConfig: Redis Testcontainer đã khởi động thành công tại {}:{}",
                        redisContainer.getHost(), redisContainer.getFirstMappedPort());
            } catch (Exception e) {
                log.warn("TestRedisConfig: Không thể khởi động Redis Testcontainer: {}", e.getMessage());
            }
        }
    }
}
