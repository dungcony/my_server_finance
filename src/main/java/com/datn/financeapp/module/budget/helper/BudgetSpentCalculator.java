package com.datn.financeapp.module.budget.helper;

import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.category.service.CategoryService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Dựng số đã chi ban đầu cho một ngân sách mới (tạo giữa kỳ hoặc vừa gia hạn) từ giao dịch chi đã có.
 * Sau đó {@code BudgetSpendService} giữ số này chính xác khi giao dịch thay đổi.
 */
@Component
@RequiredArgsConstructor
public class BudgetSpentCalculator {

    private final BudgetRepository budgetRepository;
    private final CategoryService categoryService;

    /**
     * Tổng chi trong kỳ của danh mục cùng mọi danh mục con (quy tắc nghiệp vụ 1).
     *
     * @param userId     ID người dùng
     * @param categoryId ID danh mục của ngân sách
     * @param start      ngày đầu kỳ
     * @param end        ngày cuối kỳ
     * @return tổng chi (VND), 0 nếu danh mục không còn
     */
    public long spentInPeriod(UUID userId, UUID categoryId, LocalDate start, LocalDate end) {
        List<UUID> categoryIds = categoryService.findSelfAndDescendantIds(categoryId);
        if (categoryIds.isEmpty()) {
            return 0L;
        }
        return budgetRepository.sumExpense(userId, categoryIds, start, end);
    }
}
