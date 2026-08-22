package com.datn.financeapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test khởi động context Spring đầy đủ với Testcontainers PostgreSQL 16.
 * Flyway tự chạy V1->V7 lên DB rỗng của container, Hibernate ddl-auto=validate
 * phải PASS — nghĩa là mọi entity khớp đúng cột thật (kể cả INET, JSONB).
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class SchemaSmokeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    // Testcontainers @ServiceConnection tự override spring.datasource.* nhưng
    // KHÔNG tự cấp JWT_SECRET — application.yml yêu cầu biến này bắt buộc (D-01).
    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1zY2hlbWEtc21va2UtdGVzdC0zMmI=");
    }

    @Test
    void contextLoadsAndSchemaValidates(ApplicationContext ctx) {
        assertThat(ctx).isNotNull();
    }
}
