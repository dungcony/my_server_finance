package com.datn.financeapp.module.budget.helper;

import com.datn.financeapp.module.budget.repository.BudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Dựng số đã chi ban đầu cho một ngân sách mới (tạo giữa kỳ hoặc vừa gia hạn) từ giao dịch chi đã có.
 */
@Component
@RequiredArgsConstructor
public class BudgetSpentCalculator {

    private final BudgetRepository budgetRepository;
    private final BudgetCategoryHelper categoryHelper;
    private final UserTimezoneHelper timezoneHelper;

    /**
     * Tổng chi trong kỳ của danh mục cùng mọi danh mục con (quy tắc nghiệp vụ 1).
     *
     * @param userId       ID người dùng
     * @param categoryCode Mã danh mục của ngân sách
     * @param startDate    ngày đầu kỳ
     * @param endDate      ngày cuối kỳ
     * @param zone         múi giờ của người dùng
     * @return tổng chi (VND), 0 nếu danh mục không có giao dịch
     */
    public long spentInPeriod(UUID userId, String categoryCode, LocalDate startDate, LocalDate endDate, ZoneId zone) {
        List<String> categoryCodes = categoryHelper.findDescendantCodes(categoryCode);
        if (categoryCodes.isEmpty()) {
            return 0L;
        }
        Instant startInstant = timezoneHelper.toStartInstant(startDate, zone);
        Instant endInstant = timezoneHelper.toEndInstant(endDate, zone);
        return budgetRepository.sumExpense(userId, categoryCodes, startInstant, endInstant);
    }
}
