package com.datn.financeapp.module.budget.helper;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Projection rút gọn của một ngân sách phục vụ tính toán số đã chi và cảnh báo.
 */
public record BudgetSpent(
        UUID id,
        String categoryCode,
        long limitAmount,
        long spent,
        LocalDate endDate
) {
}
