package com.datn.financeapp.module.user.enums;

import lombok.Getter;

/**
 * Gói premium có thể mua; {@code months} là số tháng được cộng vào hạn dùng.
 * Giá từng gói nằm trong cấu hình ({@code PremiumPlanProperties}), không nằm ở đây.
 */
@Getter
public enum PremiumPlan {
    PREMIUM_1M(1),
    PREMIUM_6M(6),
    PREMIUM_12M(12);

    private final int months;

    PremiumPlan(int months) {
        this.months = months;
    }
}
