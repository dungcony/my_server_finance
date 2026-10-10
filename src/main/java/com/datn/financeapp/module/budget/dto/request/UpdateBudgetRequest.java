package com.datn.financeapp.module.budget.dto.request;

import com.datn.financeapp.module.budget.enums.PeriodType;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Yêu cầu cập nhật thông tin ngân sách chi tiêu.
 *
 * @param limitAmount Hạn mức chi tiêu tối đa mới (Đơn vị: VND)
 * @param autoRenew   Trạng thái tự động gia hạn khi hết kỳ
 * @param isActive    Trạng thái bật/tắt hiệu lực của ngân sách
 * @param categoryId  ID danh mục (chỉ gửi lên để kiểm tra; không được phép sửa)
 * @param periodType  Loại kỳ ngân sách (chỉ gửi lên để kiểm tra; không được phép sửa)
 */
public record UpdateBudgetRequest(
        @Positive(message = "Hạn mức phải lớn hơn 0.") Long limitAmount,
        Boolean autoRenew,
        Boolean isActive,
        UUID categoryId,
        PeriodType periodType) {
}
