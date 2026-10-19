package com.datn.financeapp.unitTest.pay.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.module.pay.config.PayOsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PayOsPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(PayOsProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("cấu hình đầy đủ, hợp lệ -> context khởi động thành công")
    void valid() {
        runner.withPropertyValues(
                "payos.enabled=true",
                "payos.client-id=client",
                "payos.api-key=api",
                "payos.checksum-key=checksum",
                "payos.return-url=http://return",
                "payos.cancel-url=http://cancel",
                "payos.expired-minutes=15"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            PayOsProperties props = context.getBean(PayOsProperties.class);
            assertThat(props.expiredMinutes()).isEqualTo(15);
            assertThat(props.configured()).isTrue();
        });
    }

    @Test
    @DisplayName("expiredMinutes <= 0 -> context khởi động thất bại do vi phạm @Min(1)")
    void invalidExpiredMinutes() {
        runner.withPropertyValues(
                "payos.enabled=true",
                "payos.client-id=client",
                "payos.api-key=api",
                "payos.checksum-key=checksum",
                "payos.return-url=http://return",
                "payos.cancel-url=http://cancel",
                "payos.expired-minutes=0"
        ).run(context -> assertThat(context).hasFailed());

        runner.withPropertyValues(
                "payos.enabled=true",
                "payos.client-id=client",
                "payos.api-key=api",
                "payos.checksum-key=checksum",
                "payos.return-url=http://return",
                "payos.cancel-url=http://cancel",
                "payos.expired-minutes=-5"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("thiếu returnUrl hoặc cancelUrl -> context khởi động thất bại")
    void missingUrls() {
        runner.withPropertyValues(
                "payos.enabled=true",
                "payos.client-id=client",
                "payos.api-key=api",
                "payos.checksum-key=checksum",
                "payos.expired-minutes=15"
        ).run(context -> assertThat(context).hasFailed());
    }
}
