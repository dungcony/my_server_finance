package com.datn.financeapp.unitTest.budget.service.impl;

import com.datn.financeapp.module.budget.service.impl.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.budget.dto.request.CreateBudgetRequest;
import com.datn.financeapp.module.budget.dto.request.UpdateBudgetRequest;
import com.datn.financeapp.module.budget.dto.response.BudgetRes;
import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.BudgetStatus;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.BudgetSpentCalculator;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.mapper.BudgetMapper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String FOOD_CODE = "food";
    private static final String COFFEE_CODE = "food_coffee";
    private static final UUID BUDGET_ID = UUID.randomUUID();
    private static final long LIMIT = 3_000_000L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private BudgetCategoryHelper categoryHelper;
    @Mock
    private BudgetSpentCalculator spentCalculator;
    @Mock
    private UserTimezoneHelper timezoneHelper;

    private BudgetImpl budgetService;

    @BeforeEach
    void setUp() {
        when(timezoneHelper.getCurrentZone()).thenReturn(ZONE);
        budgetService = new BudgetImpl(
                budgetRepository, categoryHelper, Mappers.getMapper(BudgetMapper.class), spentCalculator, timezoneHelper);
    }

    @Test
    @DisplayName("Tạo ngân sách giữa kỳ thì số đã chi lấy theo giao dịch đã có của cả cây danh mục")
    void create_usesCalculatedInitialSpent() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        LocalDate start = PeriodType.MONTH.startOfCurrent(today);
        LocalDate end = PeriodType.MONTH.endOf(start);
        when(categoryHelper.findSummary(FOOD_CODE)).thenReturn(Optional.of(category(FOOD_CODE, "Ăn uống")));
        when(budgetRepository.existsOverlapping(USER_ID, FOOD_CODE, start, end)).thenReturn(false);
        when(spentCalculator.spentInPeriod(USER_ID, FOOD_CODE, start, end, ZONE)).thenReturn(1_200_000L);

        BudgetRes res = budgetService.create(
                USER_ID, new CreateBudgetRequest(FOOD_CODE, LIMIT, PeriodType.MONTH, start, null));

        ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).save(saved.capture());
        assertThat(saved.getValue().getSpent()).isEqualTo(1_200_000L);
        assertThat(saved.getValue().getStartDate()).isEqualTo(start);
        assertThat(saved.getValue().getEndDate()).isEqualTo(end);
        assertThat(saved.getValue().getAutoRenew()).isTrue();
        assertThat(saved.getValue().getIsActive()).isTrue();
        assertThat(res.spentAmount()).isEqualTo(1_200_000L);
        assertThat(res.remaining()).isEqualTo(1_800_000L);
        assertThat(res.status()).isEqualTo(BudgetStatus.NORMAL);
    }

    @Test
    @DisplayName("Tạo ngân sách trùng danh mục trong kỳ chồng lấn thì báo BUDGET_ALREADY_EXISTS")
    void create_overlappingBudget_throws() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        LocalDate start = PeriodType.MONTH.startOfCurrent(today);
        LocalDate end = PeriodType.MONTH.endOf(start);
        when(categoryHelper.findSummary(FOOD_CODE)).thenReturn(Optional.of(category(FOOD_CODE, "Ăn uống")));
        when(budgetRepository.existsOverlapping(USER_ID, FOOD_CODE, start, end)).thenReturn(true);

        assertThatThrownBy(() -> budgetService.create(
                USER_ID, new CreateBudgetRequest(FOOD_CODE, LIMIT, PeriodType.MONTH, start, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BUDGET_ALREADY_EXISTS.getCode());
        verify(budgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Danh sách ngân sách kèm tên danh mục và trạng thái đã tính")
    void list_attachesCategoryAndStatus() {
        when(budgetRepository.search(USER_ID, true, null)).thenReturn(List.of(budget(2_500_000L)));
        when(categoryHelper.findSummaries(anyCollection())).thenReturn(Map.of(FOOD_CODE, category(FOOD_CODE, "Ăn uống")));

        List<BudgetRes> list = budgetService.list(USER_ID, true, null);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).category().name()).isEqualTo("Ăn uống");
        assertThat(list.get(0).status()).isEqualTo(BudgetStatus.NEAR_LIMIT);
    }

    @Test
    @DisplayName("Đổi danh mục của ngân sách đã tạo thì báo BUDGET_CATEGORY_NOT_EDITABLE")
    void update_changeCategory_throws() {
        when(budgetRepository.findByIdAndUserIdAndIsDeletedFalse(BUDGET_ID, USER_ID)).thenReturn(Optional.of(budget(0L)));

        assertThatThrownBy(() -> budgetService.update(
                USER_ID, BUDGET_ID, new UpdateBudgetRequest(null, null, null, COFFEE_CODE, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BUDGET_CATEGORY_NOT_EDITABLE.getCode());
    }

    @Test
    @DisplayName("Đổi loại kỳ của ngân sách đã tạo thì báo BUDGET_CATEGORY_NOT_EDITABLE")
    void update_changePeriod_throws() {
        when(budgetRepository.findByIdAndUserIdAndIsDeletedFalse(BUDGET_ID, USER_ID)).thenReturn(Optional.of(budget(0L)));

        assertThatThrownBy(() -> budgetService.update(
                USER_ID, BUDGET_ID, new UpdateBudgetRequest(null, null, null, null, PeriodType.YEAR)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BUDGET_CATEGORY_NOT_EDITABLE.getCode());
    }

    @Test
    @DisplayName("Sửa hạn mức và tự gia hạn thì ghi đúng hai trường, giữ nguyên số đã chi")
    void update_changesLimitAndAutoRenew() {
        Budget budget = budget(900_000L);
        when(budgetRepository.findByIdAndUserIdAndIsDeletedFalse(BUDGET_ID, USER_ID)).thenReturn(Optional.of(budget));
        when(categoryHelper.findSummary(FOOD_CODE)).thenReturn(Optional.of(category(FOOD_CODE, "Ăn uống")));

        BudgetRes res = budgetService.update(
                USER_ID, BUDGET_ID, new UpdateBudgetRequest(5_000_000L, false, null, FOOD_CODE, PeriodType.MONTH));

        assertThat(budget.getLimitAmount()).isEqualTo(5_000_000L);
        assertThat(budget.getAutoRenew()).isFalse();
        assertThat(budget.getSpent()).isEqualTo(900_000L);
        assertThat(res.limitAmount()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("Bật lại ngân sách đã tắt mà trùng một ngân sách đang hiệu lực thì báo BUDGET_ALREADY_EXISTS")
    void update_reactivateOverlapping_throws() {
        Budget budget = budget(0L);
        budget.setIsActive(false);
        when(budgetRepository.findByIdAndUserIdAndIsDeletedFalse(BUDGET_ID, USER_ID)).thenReturn(Optional.of(budget));
        when(budgetRepository.existsOverlapExceptId(
                USER_ID, FOOD_CODE, budget.getStartDate(), budget.getEndDate(), BUDGET_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> budgetService.update(
                USER_ID, BUDGET_ID, new UpdateBudgetRequest(null, null, true, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BUDGET_ALREADY_EXISTS.getCode());
        assertThat(budget.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Ngân sách của người khác hoặc không tồn tại đều trả NOT_FOUND (404), không lộ sự tồn tại")
    void detail_notOwned_throwsNotFound() {
        when(budgetRepository.findByIdAndUserIdAndIsDeletedFalse(BUDGET_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.detail(USER_ID, BUDGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("Xoá ngân sách chỉ vô hiệu hoá, không xoá bản ghi")
    void delete_deactivates() {
        Budget budget = budget(0L);
        when(budgetRepository.findByIdAndUserIdAndIsDeletedFalse(BUDGET_ID, USER_ID)).thenReturn(Optional.of(budget));

        budgetService.delete(USER_ID, BUDGET_ID);

        assertThat(budget.getIsActive()).isFalse();
        assertThat(budget.getIsDeleted()).isTrue();
        verify(budgetRepository, never()).delete(any());
    }

    private static Budget budget(long spent) {
        LocalDate now = LocalDate.now();
        return Budget.builder()
                .id(BUDGET_ID)
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
