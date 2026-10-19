package com.datn.financeapp.module.budget.dto.request;

import com.datn.financeapp.module.budget.enums.PeriodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Request tạo ngân sách chi tiêu mới.
 *
 * @param categoryCode Mã danh mục chi tiêu (VD: FOOD, COFFEE...)
 * @param limitAmount  Hạn mức chi tiêu tối đa (VND, phải > 0)
 * @param periodType   Loại kỳ (WEEK, MONTH, QUARTER, YEAR)
 * @param startDate    Ngày bắt đầu kỳ (nếu null sẽ mặc định là đầu kỳ hiện tại theo múi giờ user)
 * @param autoRenew    Tự động gia hạn khi hết kỳ (mặc định true)
 */
public record CreateBudgetRequest(
        @NotBlank(message = "Mã danh mục không được để trống.")
        String categoryCode,

        @NotNull(message = "Hạn mức ngân sách không được để trống.")
        @Positive(message = "Hạn mức ngân sách phải lớn hơn 0.")
        Long limitAmount,

        @NotNull(message = "Loại kỳ ngân sách không được để trống.")
        PeriodType periodType,

        LocalDate startDate,

        Boolean autoRenew
) {
}
