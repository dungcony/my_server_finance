package com.datn.financeapp.module.user.config;

import com.datn.financeapp.module.user.enums.PremiumPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bảng giá các gói premium (VND), đọc từ biến môi trường {@code PREMIUM_PRICE_*}.
 * Không có giá mặc định: thiếu biến thì app báo lỗi ngay khi khởi động.
 */
@Validated
@ConfigurationProperties(prefix = "premium")
public record PremiumPlanProperties(@NotNull @Valid Prices prices) {

    public record Prices(
            @Min(2000) long oneMonth,
            @Min(2000) long sixMonths,
            @Min(2000) long twelveMonths
    ) {
    }

    public long priceOf(PremiumPlan plan) {
        return switch (plan) {
            case PREMIUM_1M -> prices.oneMonth();
            case PREMIUM_6M -> prices.sixMonths();
            case PREMIUM_12M -> prices.twelveMonths();
        };
    }
}
