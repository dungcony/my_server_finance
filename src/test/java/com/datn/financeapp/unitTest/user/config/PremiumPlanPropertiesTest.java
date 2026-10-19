package com.datn.financeapp.unitTest.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.module.user.config.PremiumPlanProperties;
import com.datn.financeapp.module.user.enums.PremiumPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PremiumPlanPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(PremiumPlanProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("giá các gói >= 2000 -> khởi động thành công và ánh xạ đủ mọi gói")
    void valid() {
        runner.withPropertyValues(
                "premium.prices.one-month=49000",
                "premium.prices.six-months=249000",
                "premium.prices.twelve-months=449000"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            PremiumPlanProperties props = context.getBean(PremiumPlanProperties.class);
            assertThat(props.priceOf(PremiumPlan.PREMIUM_1M)).isEqualTo(49_000L);
            assertThat(props.priceOf(PremiumPlan.PREMIUM_6M)).isEqualTo(249_000L);
            assertThat(props.priceOf(PremiumPlan.PREMIUM_12M)).isEqualTo(449_000L);
        });
    }

    @Test
    @DisplayName("giá gói < 2000 VND hoặc <= 0 -> context khởi động thất bại do vi phạm @Min(2000)")
    void invalidPrices() {
        runner.withPropertyValues(
                "premium.prices.one-month=1999",
                "premium.prices.six-months=249000",
                "premium.prices.twelve-months=449000"
        ).run(context -> assertThat(context).hasFailed());

        runner.withPropertyValues(
                "premium.prices.one-month=0",
                "premium.prices.six-months=249000",
                "premium.prices.twelve-months=449000"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("thiếu giá của một gói -> context khởi động thất bại")
    void missingPrice() {
        runner.withPropertyValues(
                "premium.prices.one-month=49000"
        ).run(context -> assertThat(context).hasFailed());
    }
}
