package com.datn.financeapp.unitTest.budget.service.impl;

import com.datn.financeapp.module.budget.service.impl.*;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.budget.dto.response.BudgetAlertRes;
import com.datn.financeapp.module.budget.dto.response.BudgetSummaryRes;
import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.BudgetStatus;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;

import java.time.LocalDate;
import java.time.ZoneId;
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
class BudgetOverviewImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String FOOD_CODE = "food";
    private static final long LIMIT = 3_000_000L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private BudgetCategoryHelper categoryHelper;
    @Mock
    private UserTimezoneHelper timezoneHelper;

    private BudgetOverviewImpl overviewService;

    @BeforeEach
    void setUp() {
        when(timezoneHelper.getCurrentZone()).thenReturn(ZONE);
        overviewService = new BudgetOverviewImpl(budgetRepository, categoryHelper, timezoneHelper);
    }

    @Test
    @DisplayName("Tổng quan cộng hạn mức, đã chi và đếm ngân sách theo trạng thái")
    void summary_aggregatesActiveBudgets() {
        when(budgetRepository.findActiveOn(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(List.of(budget(2_500_000L), budget(3_200_000L), budget(100_000L)));

        BudgetSummaryRes summary = overviewService.summary(USER_ID);

        assertThat(summary.budgetCount()).isEqualTo(3);
        assertThat(summary.totalLimit()).isEqualTo(9_000_000L);
        assertThat(summary.totalSpent()).isEqualTo(5_800_000L);
        assertThat(summary.totalRemaining()).isEqualTo(3_200_000L);
        assertThat(summary.overLimitCount()).isEqualTo(1);
        assertThat(summary.nearLimitCount()).isEqualTo(1);
        assertThat(summary.status()).isEqualTo(BudgetStatus.NORMAL);
    }

    @Test
    @DisplayName("Không có ngân sách nào thì tổng quan toàn số 0, không chia cho 0")
    void summary_noBudgets_isZero() {
        when(budgetRepository.findActiveOn(eq(USER_ID), any(LocalDate.class))).thenReturn(List.of());

        BudgetSummaryRes summary = overviewService.summary(USER_ID);

        assertThat(summary.budgetCount()).isZero();
        assertThat(summary.totalLimit()).isZero();
        assertThat(summary.ratio()).isEqualByComparingTo("0");
        assertThat(summary.status()).isEqualTo(BudgetStatus.NORMAL);
    }

    @Test
    @DisplayName("Cảnh báo chỉ gồm ngân sách từ 80%, vượt thì critical, sắp chạm thì alert, tỉ lệ cao nhất trước")
    void alerts_onlyAttentionBudgets_sortedByRatio() {
        Budget near = budget(2_500_000L);
        Budget over = budget(3_600_000L);
        Budget normal = budget(100_000L);
        when(budgetRepository.findActiveOn(eq(USER_ID), any(LocalDate.class))).thenReturn(List.of(near, normal, over));
        when(categoryHelper.findSummaries(anyCollection())).thenReturn(Map.of(FOOD_CODE, category(FOOD_CODE, "Ăn uống")));

        List<BudgetAlertRes> alerts = overviewService.alerts(USER_ID);

        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).budgetId()).isEqualTo(over.getId());
        assertThat(alerts.get(0).severity()).isEqualTo("critical");
        assertThat(alerts.get(0).status()).isEqualTo(BudgetStatus.OVER_LIMIT);
        assertThat(alerts.get(0).category()).isEqualTo("Ăn uống");
        assertThat(alerts.get(1).budgetId()).isEqualTo(near.getId());
        assertThat(alerts.get(1).severity()).isEqualTo("alert");
        assertThat(alerts.get(1).title()).isEqualTo("Sắp hết ngân sách");
    }

    private static Budget budget(long spent) {
        LocalDate now = LocalDate.now();
        return Budget.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .categoryCode(FOOD_CODE)
                .spent(spent)
                .limitAmount(LIMIT)
                .periodType(PeriodType.MONTH)
                .startDate(now.minusDays(5))
                .endDate(now.plusDays(25))
                .autoRenew(true)
                .isActive(true)
                .build();
    }

    private static CateSumaryRes category(String code, String name) {
        return new CateSumaryRes(UUID.randomUUID(), name, code, "icon_food", "#112233");
    }
}
