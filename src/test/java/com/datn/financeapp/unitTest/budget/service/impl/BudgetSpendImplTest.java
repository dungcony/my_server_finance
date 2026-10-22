package com.datn.financeapp.unitTest.budget.service.impl;

import com.datn.financeapp.module.budget.service.impl.*;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.budget.dto.response.BudgetAffectedRes;
import com.datn.financeapp.module.budget.enums.BudgetStatus;
import com.datn.financeapp.module.budget.events.BudgetAlertEvent;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.BudgetSpent;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BudgetSpendImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String FOOD_CODE = "food";
    private static final String COFFEE_CODE = "food_coffee";
    private static final UUID BUDGET_ID = UUID.randomUUID();
    private static final long LIMIT = 3_000_000L;
    private static final LocalDate END = LocalDate.now().plusDays(25);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private BudgetCategoryHelper categoryHelper;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private UserTimezoneHelper timezoneHelper;

    private BudgetSpendImpl budgetSpendService;

    @BeforeEach
    void setUp() {
        when(timezoneHelper.getCurrentZone()).thenReturn(ZONE);
        when(timezoneHelper.toLocalDate(any(Instant.class), eq(ZONE))).thenAnswer(
                inv -> ((Instant) inv.getArgument(0)).atZone(ZONE).toLocalDate());
        budgetSpendService = new BudgetSpendImpl(budgetRepository, categoryHelper, applicationEventPublisher, timezoneHelper);
    }

    @Test
    @DisplayName("Ghi chi vào danh mục con thì ngân sách đặt ở danh mục cha cũng được cộng gộp")
    void addExpense_addsToParentCategoryBudget() {
        Instant date = Instant.now();
        LocalDate localDate = date.atZone(ZONE).toLocalDate();
        stubBudgetCovering(localDate, spentView(2_000_000L), spentView(2_050_000L));

        List<BudgetAffectedRes> affected = budgetSpendService.addExpense(USER_ID, COFFEE_CODE, date, 50_000L);

        verify(budgetRepository).addSpent(List.of(BUDGET_ID), 50_000L);
        assertThat(affected).hasSize(1);
        assertThat(affected.get(0).id()).isEqualTo(BUDGET_ID);
        assertThat(affected.get(0).categoryName()).isEqualTo("Ăn uống");
        assertThat(affected.get(0).spentAmount()).isEqualTo(2_050_000L);
        assertThat(affected.get(0).status()).isEqualTo(BudgetStatus.NORMAL);
        assertThat(affected.get(0).alert()).isNull();
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    @DisplayName("Không ngân sách nào bao khoản chi thì không cập nhật gì")
    void addExpense_noBudgetCovering_doesNothing() {
        Instant date = Instant.now();
        LocalDate localDate = date.atZone(ZONE).toLocalDate();
        when(categoryHelper.findAncestorCodes(COFFEE_CODE)).thenReturn(List.of(COFFEE_CODE, FOOD_CODE));
        when(budgetRepository.findActiveByCategoryCodes(USER_ID, List.of(COFFEE_CODE, FOOD_CODE), localDate))
                .thenReturn(List.of());

        List<BudgetAffectedRes> affected = budgetSpendService.addExpense(USER_ID, COFFEE_CODE, date, 50_000L);

        assertThat(affected).isEmpty();
        verify(budgetRepository, never()).addSpent(anyCollection(), anyLong());
    }

    @Test
    @DisplayName("Khoản chi không có danh mục (vd. chuyển tiền) không đụng tới ngân sách")
    void addExpense_nullCategory_doesNothing() {
        List<BudgetAffectedRes> affected = budgetSpendService.addExpense(USER_ID, null, Instant.now(), 50_000L);

        assertThat(affected).isEmpty();
        verifyNoInteractions(budgetRepository, categoryHelper, applicationEventPublisher);
    }

    @Test
    @DisplayName("Chi làm ngân sách chạm 80% thì phát sự kiện BudgetAlertEvent và trả câu cảnh báo")
    void addExpense_crossingNearLimit_notifies() {
        Instant date = Instant.now();
        LocalDate localDate = date.atZone(ZONE).toLocalDate();
        stubBudgetCovering(localDate, spentView(2_300_000L), spentView(2_500_000L));

        List<BudgetAffectedRes> affected = budgetSpendService.addExpense(USER_ID, COFFEE_CODE, date, 200_000L);

        assertThat(affected.get(0).status()).isEqualTo(BudgetStatus.NEAR_LIMIT);
        assertThat(affected.get(0).alert()).isNotBlank();

        ArgumentCaptor<BudgetAlertEvent> captor = ArgumentCaptor.forClass(BudgetAlertEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().title()).isEqualTo("Sắp hết ngân sách");
        assertThat(captor.getValue().budgetId()).isEqualTo(BUDGET_ID);
    }

    @Test
    @DisplayName("Chi làm ngân sách vượt 100% thì cảnh báo mang tiêu đề Đã vượt")
    void addExpense_overLimit_notifiesWithOverTitle() {
        Instant date = Instant.now();
        LocalDate localDate = date.atZone(ZONE).toLocalDate();
        stubBudgetCovering(localDate, spentView(2_900_000L), spentView(3_200_000L));

        budgetSpendService.addExpense(USER_ID, COFFEE_CODE, date, 300_000L);

        ArgumentCaptor<BudgetAlertEvent> captor = ArgumentCaptor.forClass(BudgetAlertEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().title()).isEqualTo("Đã vượt ngân sách");
        assertThat(captor.getValue().budgetId()).isEqualTo(BUDGET_ID);
    }

    private void stubBudgetCovering(LocalDate date, BudgetSpent initial, BudgetSpent afterAdd) {
        when(categoryHelper.findAncestorCodes(COFFEE_CODE)).thenReturn(List.of(COFFEE_CODE, FOOD_CODE));
        when(budgetRepository.findActiveByCategoryCodes(USER_ID, List.of(COFFEE_CODE, FOOD_CODE), date))
                .thenReturn(List.of(initial));
        when(budgetRepository.findSpentByIds(List.of(BUDGET_ID))).thenReturn(List.of(afterAdd));
        when(categoryHelper.findSummaries(anyCollection())).thenReturn(Map.of(FOOD_CODE, category(FOOD_CODE, "Ăn uống")));
    }

    private static BudgetSpent spentView(long spent) {
        return new BudgetSpent(BUDGET_ID, FOOD_CODE, LIMIT, spent, END);
    }

    private static CateSumaryRes category(String code, String name) {
        return new CateSumaryRes(UUID.randomUUID(), name, code, "icon_food", "#112233");
    }
}
