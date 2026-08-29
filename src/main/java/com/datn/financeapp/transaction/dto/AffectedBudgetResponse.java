package com.datn.financeapp.transaction.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Phần tử của {@code affected_budgets} trong response POST/PUT/duplicate transactions
 * (api/04-GIAO-DICH.md mục 4, dòng 249-262). Map trực tiếp từ
 * {@code BudgetProgressRepository.BudgetProgressProjection}.
 */
public record AffectedBudgetResponse(
        UUID id,
        String category,
        Long limitAmount,
        Long spentAmount,
        BigDecimal ratio,
        String status,
        String alert) {
}
