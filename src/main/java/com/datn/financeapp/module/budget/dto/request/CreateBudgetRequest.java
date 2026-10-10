package com.datn.financeapp.module.budget.dto.request;

import com.datn.financeapp.module.budget.enums.PeriodType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu tạo mới ngân sách chi tiêu.
 *
 * @param categoryId  ID danh mục áp dụng ngân sách
 * @param limitAmount Hạn mức chi tiêu tối đa (Đơn vị: VND)
 * @param periodType  Loại kỳ ngân sách ({@code week}, {@code month}, {@code quarter}, {@code year})
 * @param startDate   Ngày bắt đầu kỳ ngân sách; nếu để trống mặc định là đầu kỳ hiện tại
 * @param autoRenew   Tự động chuyển kỳ mới khi hết hạn (mặc định: true)
 */
public record CreateBudgetRequest(
        @NotNull(message = "Thiếu danh mục cho ngân sách.") UUID categoryId,
        @NotNull(message = "Thiếu hạn mức.") @Positive(message = "Hạn mức phải lớn hơn 0.") Long limitAmount,
        @NotNull(message = "Thiếu loại kỳ ngân sách.") PeriodType periodType,
        LocalDate startDate,
        Boolean autoRenew) {
}
