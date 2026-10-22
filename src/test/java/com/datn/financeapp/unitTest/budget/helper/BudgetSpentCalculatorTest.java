package com.datn.financeapp.unitTest.budget.helper;

import com.datn.financeapp.module.budget.helper.*;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.budget.repository.BudgetRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private static final String FOOD_CODE = "food";
    private static final String COFFEE_CODE = "food_coffee";
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private BudgetCategoryHelper categoryHelper;
    @Mock
    private UserTimezoneHelper timezoneHelper;

    @InjectMocks
    private BudgetSpentCalculator calculator;

    @Test
    @DisplayName("Tổng chi tính cho cả danh mục và mọi danh mục con của nó")
    void spentInPeriod_includesDescendants() {
        when(categoryHelper.findDescendantCodes(FOOD_CODE)).thenReturn(List.of(FOOD_CODE, COFFEE_CODE));
        when(timezoneHelper.toStartInstant(START, ZONE)).thenReturn(Instant.now());
        when(timezoneHelper.toEndInstant(END, ZONE)).thenReturn(Instant.now());
        when(budgetRepository.sumExpense(any(), anyCollection(), any(), any())).thenReturn(1_200_000L);

        assertThat(calculator.spentInPeriod(USER_ID, FOOD_CODE, START, END, ZONE)).isEqualTo(1_200_000L);
    }

    @Test
    @DisplayName("Danh mục không còn tồn tại thì tổng chi là 0 và không truy vấn giao dịch")
    void spentInPeriod_unknownCategory_isZero() {
        when(categoryHelper.findDescendantCodes(FOOD_CODE)).thenReturn(List.of());

        assertThat(calculator.spentInPeriod(USER_ID, FOOD_CODE, START, END, ZONE)).isZero();
        verify(budgetRepository, never()).sumExpense(any(), anyCollection(), any(), any());
    }
}
