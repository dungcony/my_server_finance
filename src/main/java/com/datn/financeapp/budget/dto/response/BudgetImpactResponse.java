package com.datn.financeapp.budget.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một ngân sách bị ảnh hưởng bởi khoản chi vừa ghi, kèm câu cảnh báo đã dựng sẵn.
 *
 * <p>Trả về ngay trong phản hồi ghi giao dịch (api/04 mục 6) — người dùng cần biết khoản vừa ghi
 * ăn vào ngân sách nào, không đợi được thông báo bất đồng bộ.
 *
 * <p>Module ngân sách sở hữu DTO này vì chính nó biết thế nào là "vượt hạn mức" và câu cảnh báo
 * phải viết ra sao; {@code transaction/} chỉ ánh xạ lại sang DTO của mình.
 */
public record BudgetImpactResponse(
        UUID id,
        String categoryName,
        Long limitAmount,
        Long spentAmount,
        BigDecimal ratio,
        String status,
        String alert) {}
