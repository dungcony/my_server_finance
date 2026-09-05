package com.datn.financeapp.budget.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body {@code POST /budgets} (api/05 mục 4). {@code startDate} rỗng = đầu kỳ hiện tại;
 * {@code endDate} KHÔNG nhận từ client mà backend tự tính từ {@code periodType}.
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
