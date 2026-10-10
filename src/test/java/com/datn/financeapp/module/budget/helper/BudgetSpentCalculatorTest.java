package com.datn.financeapp.module.budget.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.category.service.CategoryService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetSpentCalculatorTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FOOD_ID = UUID.randomUUID();
    private static final UUID COFFEE_ID = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private CategoryService categoryService;
    @InjectMocks
    private BudgetSpentCalculator calculator;

    @Test
    @DisplayName("Tổng chi tính cho cả danh mục và mọi danh mục con của nó")
    void spentInPeriod_includesDescendants() {
        when(categoryService.findSelfAndDescendantIds(FOOD_ID)).thenReturn(List.of(FOOD_ID, COFFEE_ID));
        when(budgetRepository.sumExpense(USER_ID, List.of(FOOD_ID, COFFEE_ID), START, END)).thenReturn(1_200_000L);

        assertThat(calculator.spentInPeriod(USER_ID, FOOD_ID, START, END)).isEqualTo(1_200_000L);
    }

    @Test
    @DisplayName("Danh mục không còn tồn tại thì tổng chi là 0 và không truy vấn giao dịch")
    void spentInPeriod_unknownCategory_isZero() {
        when(categoryService.findSelfAndDescendantIds(FOOD_ID)).thenReturn(List.of());

        assertThat(calculator.spentInPeriod(USER_ID, FOOD_ID, START, END)).isZero();
        verify(budgetRepository, never()).sumExpense(any(), anyCollection(), any(), any());
    }
}
