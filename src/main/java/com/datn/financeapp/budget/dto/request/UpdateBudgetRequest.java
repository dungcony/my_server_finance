package com.datn.financeapp.budget.dto.request;

import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Body {@code PATCH /budgets/{id}} (api/05 mục 5). Sửa được: {@code limitAmount},
 * {@code autoRenew}, {@code isActive}, {@code walletId}.
 *
 * <p>{@code categoryId}/{@code periodType} vẫn khai ở đây CÓ CHỦ ĐÍCH dù không sửa được: nhận vào
 * rồi từ chối tường minh bằng {@code CATEGORY_NOT_EDITABLE} (400) rõ ràng hơn nhiều so với im
 * lặng bỏ qua trường client gửi lên — client sẽ tưởng đã đổi thành công.
 */
public record UpdateBudgetRequest(
        @Positive(message = "Hạn mức phải lớn hơn 0.") Long limitAmount,
        Boolean autoRenew,
        Boolean isActive,
        UUID walletId,
        UUID categoryId,
        String periodType) {
}
