package com.datn.financeapp.module.budget.dto.response;

import com.datn.financeapp.module.budget.enums.BudgetStatus;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.category.dto.response.CategoryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một ngân sách kèm tiến độ chi tiêu.
 *
 * @param id            ID ngân sách
 * @param category      danh mục áp dụng ({@code null} nếu danh mục không còn)
 * @param limitAmount   hạn mức chi (VND)
 * @param spentAmount   số đã chi trong kỳ (VND), đã cộng gộp danh mục con
 * @param remaining     còn lại, âm khi vượt hạn mức
 * @param ratio         tỉ lệ đã chi trên hạn mức (4 chữ số thập phân)
 * @param status        trạng thái: bình thường, sắp chạm (từ 80%), đã vượt (từ 100%)
 * @param periodType    loại kỳ
 * @param startDate     ngày đầu kỳ
 * @param endDate       ngày cuối kỳ
 * @param daysRemaining số ngày còn lại của kỳ, tối thiểu 0
 * @param autoRenew     tự tạo ngân sách kỳ mới khi hết kỳ
 * @param isActive      còn hiệu lực
 */
public record BudgetRes(
        UUID id,
        CategoryResponse category,
        long limitAmount,
        long spentAmount,
        long remaining,
        BigDecimal ratio,
        BudgetStatus status,
        PeriodType periodType,
        LocalDate startDate,
        LocalDate endDate,
        int daysRemaining,
        boolean autoRenew,
        boolean isActive) {
}
