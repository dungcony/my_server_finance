package com.datn.financeapp.module.budget.dto.response;

import com.datn.financeapp.module.budget.enums.BudgetStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ngân sách bị ảnh hưởng bởi một khoản chi vừa ghi, sau khi {@code spent} đã cập nhật.
 *
 * @param id           ID ngân sách
 * @param categoryName tên danh mục của ngân sách
 * @param limitAmount  hạn mức
 * @param spentAmount  số đã chi sau khi cập nhật
 * @param ratio        tỉ lệ đã chi trên hạn mức
 * @param status       trạng thái theo tỉ lệ
 * @param alert        câu cảnh báo, {@code null} khi trạng thái bình thường
 */
public record BudgetAffectedRes(
        UUID id,
        String categoryName,
        Long limitAmount,
        Long spentAmount,
        BigDecimal ratio,
        BudgetStatus status,
        String alert) {
}
