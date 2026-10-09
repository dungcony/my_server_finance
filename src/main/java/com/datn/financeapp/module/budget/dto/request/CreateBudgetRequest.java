package com.datn.financeapp.module.budget.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu tạo mới ngân sách chi tiêu.
 *
 * @param categoryId  ID danh mục chi tiêu áp dụng ngân sách
 * @param limitAmount Hạn mức chi tiêu tối đa (Đơn vị: VND)
 * @param periodType  Loại kỳ ngân sách ({@code week}, {@code month}, {@code quarter}, {@code year})
 * @param startDate   Ngày bắt đầu kỳ ngân sách; nếu để trống mặc định là đầu kỳ hiện tại
 * @param walletId    ID ví áp dụng (tùy chọn; null nếu áp dụng cho toàn bộ ví)
 * @param autoRenew   Tự động chuyển kỳ mới khi hết hạn (mặc định: true)
 */
public record CreateBudgetRequest(
        @NotNull(message = "Thiếu danh mục cho ngân sách.") UUID categoryId,
        @NotNull(message = "Thiếu hạn mức.") @Positive(message = "Hạn mức phải lớn hơn 0.") Long limitAmount,
        @NotBlank(message = "Thiếu loại kỳ ngân sách.")
        @Pattern(
                regexp = "week|month|quarter|year",
                message = "Loại kỳ phải là week, month, quarter hoặc year.")
        String periodType,
        LocalDate startDate,
        UUID walletId,
        Boolean autoRenew) {
}
