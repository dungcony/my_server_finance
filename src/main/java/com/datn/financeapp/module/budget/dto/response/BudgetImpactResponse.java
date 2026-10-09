package com.datn.financeapp.module.budget.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một ngân sách bị ảnh hưởng bởi khoản chi vừa ghi, kèm câu cảnh báo đã dựng sẵn.
 *
 * @param id           ID của ngân sách bị ảnh hưởng
 * @param categoryName Tên danh mục của ngân sách
 * @param limitAmount  Hạn mức tối đa của ngân sách (Đơn vị: VND)
 * @param spentAmount  Số tiền đã chi trong kỳ (Đơn vị: VND)
 * @param ratio        Tỷ lệ đã chi so với hạn mức (VD: 1.10 = 110%)
 * @param status       Trạng thái tiến độ ngân sách (VD: {@code near_limit}, {@code over_limit})
 * @param alert        Câu thông báo/cảnh báo hiển thị cho người dùng
 */
public record BudgetImpactResponse(
        UUID id,
        String categoryName,
        Long limitAmount,
        Long spentAmount,
        BigDecimal ratio,
        String status,
        String alert) {
}
