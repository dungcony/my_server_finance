package com.datn.financeapp.module.budget.service.impl;

import com.datn.financeapp.module.budget.dto.response.BudgetAffectedRes;
import com.datn.financeapp.module.budget.helper.BudgetAlertText;
import com.datn.financeapp.module.budget.helper.BudgetProgress;
import com.datn.financeapp.module.budget.helper.BudgetSpent;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.budget.service.BudgetSpendService;
import com.datn.financeapp.module.category.dto.response.CategoryResponse;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.notification.service.NotificationService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai {@link BudgetSpendService}. Cả hai hàm cùng đi qua {@link #changeSpent}: tìm các ngân sách mà
 * khoản chi rơi vào (danh mục của giao dịch và mọi tổ tiên), cộng/trừ {@code spent} bằng một câu lệnh
 * nguyên tử rồi đọc lại số mới. Dấu cộng/trừ chỉ nằm ở đây.
 */
@Service
@RequiredArgsConstructor
public class BudgetSpendImpl implements BudgetSpendService {

    private static final String UNKNOWN_CATEGORY_NAME = "Danh mục đã xoá";

    private final BudgetRepository budgetRepository;
    private final CategoryService categoryService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public List<BudgetAffectedRes> addExpense(UUID userId, UUID categoryId, LocalDate date, long amount) {
        List<BudgetSpent> updated = changeSpent(userId, categoryId, date, amount);
        if (updated.isEmpty()) {
            return List.of();
        }

        Map<UUID, CategoryResponse> categories = categoryService.findAllByIds(
                updated.stream().map(BudgetSpent::categoryId).collect(Collectors.toSet()));
        LocalDate today = LocalDate.now();

        return updated.stream()
                .map(budget -> toAffected(userId, budget, categories.get(budget.categoryId()), today))
                .toList();
    }

    @Override
    @Transactional
    public void removeExpense(UUID userId, UUID categoryId, LocalDate date, long amount) {
        changeSpent(userId, categoryId, date, -amount);
    }

    // Trả các ngân sách bị ảnh hưởng với số đã chi SAU khi cập nhật; rỗng nếu không có ngân sách nào bao khoản chi
    private List<BudgetSpent> changeSpent(UUID userId, UUID categoryId, LocalDate date, long delta) {
        if (categoryId == null || delta == 0) {
            return List.of();
        }

        // Ghi vào danh mục con phải cộng cả ngân sách đặt ở mọi tổ tiên (quy tắc nghiệp vụ 1)
        List<UUID> categoryIds = categoryService.findSelfAndAncestorIds(categoryId);
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        List<UUID> budgetIds = budgetRepository.findActiveByCategoryIds(userId, categoryIds, date).stream()
                .map(BudgetSpent::id)
                .toList();
        if (budgetIds.isEmpty()) {
            return List.of();
        }

        budgetRepository.addSpent(budgetIds, delta);
        return budgetRepository.findSpentByIds(budgetIds);
    }

    private BudgetAffectedRes toAffected(UUID userId, BudgetSpent budget, CategoryResponse category, LocalDate today) {
        BudgetProgress progress = BudgetProgress.of(budget.limitAmount(), budget.spent(), budget.endDate(), today);
        boolean needsAttention = progress.status().needsAttention();

        String alert = needsAttention ? BudgetAlertText.content(progress) : null;
        if (needsAttention) {
            // Chống trùng trong ngày do ràng buộc UNIQUE ở bảng notifications, không kiểm tra ở Java
            notificationService.createBudgetAlert(userId, BudgetAlertText.title(progress.status()), alert, budget.id());
        }
        String categoryName = category != null ? category.name() : UNKNOWN_CATEGORY_NAME;
        return new BudgetAffectedRes(
                budget.id(), categoryName, budget.limitAmount(), budget.spent(), progress.ratio(), progress.status(),
                alert);
    }
}
