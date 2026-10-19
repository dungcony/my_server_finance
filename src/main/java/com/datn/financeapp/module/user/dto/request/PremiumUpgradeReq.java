package com.datn.financeapp.module.user.dto.request;

import com.datn.financeapp.module.user.enums.PremiumPlan;
import jakarta.validation.constraints.NotNull;

/**
 * Body của {@code POST /users/me/premium-upgrades}. Client chỉ chọn gói, giá do server tính.
 */
public record PremiumUpgradeReq(@NotNull PremiumPlan planCode) {
}
