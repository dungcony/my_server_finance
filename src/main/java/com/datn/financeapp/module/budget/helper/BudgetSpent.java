package com.datn.financeapp.module.budget.helper;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bộ số tối thiểu của một ngân sách để tính tiến độ. Đọc thẳng từ DB bằng truy vấn chọn cột (không qua
 * entity) nên không bị persistence context giữ bản cũ sau khi {@code BudgetRepository.addSpent} cập nhật
 * hàng loạt.
 *
 * @param id          ID ngân sách
 * @param categoryId  ID danh mục của ngân sách
 * @param limitAmount hạn mức
 * @param spent       số đã chi
 * @param endDate     ngày cuối kỳ
 */
public record BudgetSpent(UUID id, UUID categoryId, Long limitAmount, Long spent, LocalDate endDate) {
}
