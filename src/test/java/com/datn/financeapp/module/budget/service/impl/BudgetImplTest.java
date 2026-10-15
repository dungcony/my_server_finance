package com.datn.financeapp.module.budget.service.impl;

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
import com.datn.financeapp.module.budget.helper.BudgetSpentCalculator;
import com.datn.financeapp.module.budget.mapper.BudgetMapper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.service.CategoryService;

import java.time.Instant;
import java.time.LocalDate;
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

@ExtendWith(MockitoExtension.class)
class BudgetImplTest {

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
    private BudgetSpentCalculator spentCalculator;

    private BudgetImpl budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetImpl(
                budgetRepository, categoryService, Mappers.getMapper(BudgetMapper.class), spentCalculator);
    }

    @Test
    @DisplayName("Tạo ngân sách giữa kỳ thì số đã chi lấy theo giao dịch đã có của cả cây danh mục")
    void create_usesCalculatedInitialSpent() {
        LocalDate start = PeriodType.MONTH.startOfCurrent(LocalDate.now());
        LocalDate end = PeriodType.MONTH.endOf(start);
        when(categoryService.findActiveById(FOOD_ID)).thenReturn(category(FOOD_ID, "Ăn uống"));
        when(budgetRepository.existsOverlapping(USER_ID, FOOD_ID, start, end)).thenReturn(false);
        when(spentCalculator.spentInPeriod(USER_ID, FOOD_ID, start, end)).thenReturn(1_200_000L);

        BudgetRes res = budgetService.create(
                USER_ID, new CreateBudgetRequest(FOOD_ID, LIMIT, PeriodType.MONTH, null, null));

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
        LocalDate start = PeriodType.MONTH.startOfCurrent(LocalDate.now());
        when(categoryService.findActiveById(FOOD_ID)).thenReturn(category(FOOD_ID, "Ăn uống"));
        when(budgetRepository.existsOverlapping(USER_ID, FOOD_ID, start, PeriodType.MONTH.endOf(start)))
                .thenReturn(true);

        assertThatThrownBy(() -> budgetService.create(
                USER_ID, new CreateBudgetRequest(FOOD_ID, LIMIT, PeriodType.MONTH, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BUDGET_ALREADY_EXISTS.getCode());
        verify(budgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Danh sách ngân sách kèm tên danh mục và trạng thái đã tính")
    void list_attachesCategoryAndStatus() {
        when(budgetRepository.search(USER_ID, true, null)).thenReturn(List.of(budget(2_500_000L)));
        when(categoryService.findAllByIds(anyCollection())).thenReturn(Map.of(FOOD_ID, category(FOOD_ID, "Ăn uống")));

        List<BudgetRes> list = budgetService.list(USER_ID, true, null);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).category().name()).isEqualTo("Ăn uống");
        assertThat(list.get(0).status()).isEqualTo(BudgetStatus.NEAR_LIMIT);
    }

    @Test
    @DisplayName("Đổi danh mục của ngân sách đã tạo thì báo BUDGET_CATEGORY_NOT_EDITABLE")
    void update_changeCategory_throws() {
        when(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).thenReturn(Optional.of(budget(0L)));

        assertThatThrownBy(() -> budgetService.update(
                USER_ID, BUDGET_ID, new UpdateBudgetRequest(null, null, null, COFFEE_ID, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BUDGET_CATEGORY_NOT_EDITABLE.getCode());
    }

    @Test
    @DisplayName("Đổi loại kỳ của ngân sách đã tạo thì báo BUDGET_CATEGORY_NOT_EDITABLE")
    void update_changePeriod_throws() {
        when(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).thenReturn(Optional.of(budget(0L)));

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
        when(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).thenReturn(Optional.of(budget));
        when(categoryService.findAllByIds(anyCollection())).thenReturn(Map.of(FOOD_ID, category(FOOD_ID, "Ăn uống")));

        BudgetRes res = budgetService.update(
                USER_ID, BUDGET_ID, new UpdateBudgetRequest(5_000_000L, false, null, FOOD_ID, PeriodType.MONTH));

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
        when(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).thenReturn(Optional.of(budget));
        when(budgetRepository.existsOverlapExceptId(
                USER_ID, FOOD_ID, budget.getStartDate(), budget.getEndDate(), BUDGET_ID))
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
        when(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.detail(USER_ID, BUDGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("Xoá ngân sách chỉ vô hiệu hoá, không xoá bản ghi")
    void delete_deactivates() {
        Budget budget = budget(0L);
        when(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).thenReturn(Optional.of(budget));

        budgetService.delete(USER_ID, BUDGET_ID);

        assertThat(budget.getIsActive()).isFalse();
        verify(budgetRepository, never()).delete(any());
    }

    private static Budget budget(long spent) {
        return Budget.builder()
                .id(BUDGET_ID)
                .userId(USER_ID)
                .categoryId(FOOD_ID)
                .spent(spent)
                .limitAmount(LIMIT)
                .periodType(PeriodType.MONTH)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(25))
                .autoRenew(true)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }

    private static CategoryRes category(UUID id, String name) {
        return new CategoryRes(id, name, name.toUpperCase(), null, null, null, "#112233", 0, true,
                Instant.now(), false);
    }
}
