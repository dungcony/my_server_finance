package com.datn.financeapp.unitTest.budget.service.impl;

import com.datn.financeapp.module.budget.service.impl.*;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.BudgetSpentCalculator;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class BudgetRenewalImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String FOOD_CODE = "food";
    private static final UUID BUDGET_ID = UUID.randomUUID();
    private static final long LIMIT = 3_000_000L;
    private static final LocalDate NEW_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate NEW_END = LocalDate.of(2026, 10, 31);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private BudgetCategoryHelper categoryHelper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BudgetSpentCalculator spentCalculator;
    @Mock
    private TransactionTemplate transactionTemplate;

    private BudgetRenewalImpl renewalService;

    @BeforeEach
    void setUp() {
        renewalService = new BudgetRenewalImpl(
                budgetRepository, categoryHelper, notificationService, spentCalculator, transactionTemplate);
    }

    @Test
    @DisplayName("Gia hạn: tạo kỳ mới nối tiếp với số đã chi tính lại, tắt kỳ cũ và gửi thông báo")
    void renewExpiredBudgets_createsNextPeriod() {
        Budget old = expiredSeptemberBudget();
        stubExpiredBudget(old);
        when(budgetRepository.existsOverlapping(USER_ID, FOOD_CODE, NEW_START, NEW_END)).thenReturn(false);
        when(spentCalculator.spentInPeriod(USER_ID, FOOD_CODE, NEW_START, NEW_END, DEFAULT_ZONE)).thenReturn(150_000L);
        when(categoryHelper.findSummary(FOOD_CODE)).thenReturn(Optional.of(category(FOOD_CODE, "Ăn uống")));

        renewalService.renewExpiredBudgets();

        assertThat(old.getIsActive()).isFalse();
        ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).save(saved.capture());
        assertThat(saved.getValue().getStartDate()).isEqualTo(NEW_START);
        assertThat(saved.getValue().getEndDate()).isEqualTo(NEW_END);
        assertThat(saved.getValue().getLimitAmount()).isEqualTo(LIMIT);
        assertThat(saved.getValue().getSpent()).isEqualTo(150_000L);
        assertThat(saved.getValue().getAutoRenew()).isTrue();
        assertThat(saved.getValue().getIsActive()).isTrue();
        verify(notificationService).createNotification(
                eq(USER_ID), eq(NotiType.BUDGET_RENEWED), anyString(), anyString(), eq(saved.getValue().getId()));
    }

    @Test
    @DisplayName("Gia hạn: kỳ mới đã tồn tại (job chạy lại) thì bỏ qua, không tạo trùng")
    void renewExpiredBudgets_skipsWhenNextPeriodExists() {
        stubExpiredBudget(expiredSeptemberBudget());
        when(budgetRepository.existsOverlapping(USER_ID, FOOD_CODE, NEW_START, NEW_END)).thenReturn(true);

        renewalService.renewExpiredBudgets();

        verify(budgetRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Gia hạn: một ngân sách lỗi không làm hỏng các ngân sách còn lại")
    void renewExpiredBudgets_oneFailureDoesNotStopTheRest() {
        UUID failingId = UUID.randomUUID();
        when(budgetRepository.findExpiredAutoRenewIds(any(LocalDate.class))).thenReturn(List.of(failingId, BUDGET_ID));
        runTransactionTemplateInline();
        when(budgetRepository.findById(failingId)).thenThrow(new IllegalStateException("lỗi giả lập"));
        Budget old = expiredSeptemberBudget();
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(old));
        when(budgetRepository.existsOverlapping(USER_ID, FOOD_CODE, NEW_START, NEW_END)).thenReturn(false);
        when(categoryHelper.findSummary(FOOD_CODE)).thenReturn(Optional.of(category(FOOD_CODE, "Ăn uống")));

        renewalService.renewExpiredBudgets();

        assertThat(old.getIsActive()).isFalse();
        verify(budgetRepository).save(any(Budget.class));
    }

    private void stubExpiredBudget(Budget old) {
        when(budgetRepository.findExpiredAutoRenewIds(any(LocalDate.class))).thenReturn(List.of(BUDGET_ID));
        runTransactionTemplateInline();
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(old));
    }

    @SuppressWarnings("unchecked")
    private void runTransactionTemplateInline() {
        doAnswer(invocation -> {
            ((Consumer<Object>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private static Budget expiredSeptemberBudget() {
        LocalDate septStart = LocalDate.of(2026, 9, 1);
        LocalDate septEnd = LocalDate.of(2026, 9, 30);
        return Budget.builder()
                .id(BUDGET_ID)
                .userId(USER_ID)
                .categoryCode(FOOD_CODE)
                .spent(2_900_000L)
                .limitAmount(LIMIT)
                .periodType(PeriodType.MONTH)
                .startDate(septStart)
                .endDate(septEnd)
                .autoRenew(true)
                .isActive(true)
                .build();
    }

    private static CateSumaryRes category(String code, String name) {
        return new CateSumaryRes(UUID.randomUUID(), name, code, "icon_food", "#112233");
    }
}
