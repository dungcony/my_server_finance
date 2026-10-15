package com.datn.financeapp.module.budget.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.budget.dto.response.BudgetAffectedRes;
import com.datn.financeapp.module.budget.enums.BudgetStatus;
import com.datn.financeapp.module.budget.helper.BudgetSpent;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.notification.service.NotificationService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetSpendImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FOOD_ID = UUID.randomUUID();
    private static final UUID COFFEE_ID = UUID.randomUUID();
    private static final UUID BUDGET_ID = UUID.randomUUID();
    private static final long LIMIT = 3_000_000L;

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private NotificationService notificationService;

    private BudgetSpendImpl budgetSpendService;

    @BeforeEach
    void setUp() {
        budgetSpendService = new BudgetSpendImpl(budgetRepository, categoryService, notificationService);
    }

    @Test
    @DisplayName("Ghi chi vào danh mục con thì ngân sách đặt ở danh mục cha cũng được cộng gộp")
    void addExpense_addsToParentCategoryBudget() {
        LocalDate date = LocalDate.now();
        stubBudgetCovering(date, spentView(2_000_000L), spentView(2_050_000L));

        List<BudgetAffectedRes> affected = budgetSpendService.addExpense(USER_ID, COFFEE_ID, date, 50_000L);

        verify(budgetRepository).addSpent(List.of(BUDGET_ID), 50_000L);
        assertThat(affected).hasSize(1);
        assertThat(affected.get(0).id()).isEqualTo(BUDGET_ID);
        assertThat(affected.get(0).categoryName()).isEqualTo("Ăn uống");
        assertThat(affected.get(0).spentAmount()).isEqualTo(2_050_000L);
        assertThat(affected.get(0).status()).isEqualTo(BudgetStatus.NORMAL);
        assertThat(affected.get(0).alert()).isNull();
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Không ngân sách nào bao khoản chi thì không cập nhật gì")
    void addExpense_noBudgetCovering_doesNothing() {
        LocalDate date = LocalDate.now();
        when(categoryService.findSelfAndAncestorIds(COFFEE_ID)).thenReturn(List.of(COFFEE_ID, FOOD_ID));
        when(budgetRepository.findActiveByCategoryIds(USER_ID, List.of(COFFEE_ID, FOOD_ID), date))
                .thenReturn(List.of());

        List<BudgetAffectedRes> affected = budgetSpendService.addExpense(USER_ID, COFFEE_ID, date, 50_000L);

        assertThat(affected).isEmpty();
        verify(budgetRepository, never()).addSpent(anyCollection(), anyLong());
    }

    @Test
    @DisplayName("Khoản chi không có danh mục (vd. chuyển tiền) không đụng tới ngân sách")
    void addExpense_nullCategory_doesNothing() {
        List<BudgetAffectedRes> affected = budgetSpendService.addExpense(USER_ID, null, LocalDate.now(), 50_000L);

        assertThat(affected).isEmpty();
        verifyNoInteractions(budgetRepository, categoryService, notificationService);
    }

    @Test
    @DisplayName("Chi làm ngân sách chạm 80% thì ghi cảnh báo và trả câu cảnh báo")
    void addExpense_crossingNearLimit_notifies() {
        LocalDate date = LocalDate.now();
        stubBudgetCovering(date, spentView(2_300_000L), spentView(2_500_000L));

        List<BudgetAffectedRes> affected = budgetSpendService.addExpense(USER_ID, COFFEE_ID, date, 200_000L);

        assertThat(affected.get(0).status()).isEqualTo(BudgetStatus.NEAR_LIMIT);
        assertThat(affected.get(0).alert()).isNotBlank();
        verify(notificationService).createBudgetAlert(eq(USER_ID), eq("Sắp hết ngân sách"), anyString(), eq(BUDGET_ID));
    }

    @Test
    @DisplayName("Chi làm ngân sách vượt 100% thì cảnh báo mang tiêu đề Đã vượt")
    void addExpense_overLimit_notifiesWithOverTitle() {
        LocalDate date = LocalDate.now();
        stubBudgetCovering(date, spentView(2_900_000L), spentView(3_200_000L));

        budgetSpendService.addExpense(USER_ID, COFFEE_ID, date, 300_000L);

        verify(notificationService).createBudgetAlert(eq(USER_ID), eq("Đã vượt ngân sách"), anyString(), eq(BUDGET_ID));
    }

    @Test
    @DisplayName("Trừ lại khoản chi thì truyền số âm cho repository và không bao giờ cảnh báo, kể cả khi còn vượt")
    void removeExpense_subtractsAndNeverNotifies() {
        LocalDate date = LocalDate.now();
        when(categoryService.findSelfAndAncestorIds(FOOD_ID)).thenReturn(List.of(FOOD_ID));
        when(budgetRepository.findActiveByCategoryIds(USER_ID, List.of(FOOD_ID), date))
                .thenReturn(List.of(spentView(3_500_000L)));
        when(budgetRepository.findSpentByIds(List.of(BUDGET_ID))).thenReturn(List.of(spentView(3_400_000L)));

        budgetSpendService.removeExpense(USER_ID, FOOD_ID, date, 100_000L);

        verify(budgetRepository).addSpent(List.of(BUDGET_ID), -100_000L);
        verify(notificationService, never()).createBudgetAlert(any(), anyString(), anyString(), any());
    }

    // Giao dịch ghi ở COFFEE, ngân sách đặt ở FOOD (cha); spent trước và sau khi cập nhật
    private void stubBudgetCovering(LocalDate date, BudgetSpent before, BudgetSpent after) {
        when(categoryService.findSelfAndAncestorIds(COFFEE_ID)).thenReturn(List.of(COFFEE_ID, FOOD_ID));
        when(budgetRepository.findActiveByCategoryIds(USER_ID, List.of(COFFEE_ID, FOOD_ID), date))
                .thenReturn(List.of(before));
        when(budgetRepository.findSpentByIds(List.of(BUDGET_ID))).thenReturn(List.of(after));
        when(categoryService.findAllByIds(anyCollection())).thenReturn(Map.of(FOOD_ID, category(FOOD_ID, "Ăn uống")));
    }

    private static BudgetSpent spentView(long spent) {
        return new BudgetSpent(BUDGET_ID, FOOD_ID, LIMIT, spent, LocalDate.now().plusDays(10));
    }

    private static CategoryRes category(UUID id, String name) {
        return new CategoryRes(id, name, name.toUpperCase(), null, null, null, "#112233", 0, true,
                Instant.now(), false);
    }
}
