package com.datn.financeapp.unitTest.budget.service.impl;

import com.datn.financeapp.module.budget.service.impl.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.budget.dto.response.BudgetAdminRes;
import com.datn.financeapp.module.budget.entity.Budget;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.helper.BudgetCategoryHelper;
import com.datn.financeapp.module.budget.helper.UserTimezoneHelper;
import com.datn.financeapp.module.budget.mapper.BudgetMapper;
import com.datn.financeapp.module.budget.repository.BudgetRepository;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetAdminServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BUDGET_ID = UUID.randomUUID();
    private static final String FOOD_CODE = "food";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private BudgetCategoryHelper categoryHelper;
    @Mock
    private UserTimezoneHelper timezoneHelper;

    private BudgetAdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        when(timezoneHelper.getCurrentZone()).thenReturn(ZONE);
        adminService = new BudgetAdminServiceImpl(
                budgetRepository,
                categoryHelper,
                Mappers.getMapper(BudgetMapper.class),
                timezoneHelper);
    }

    @Test
    @DisplayName("Admin list: phân trang và ghép tóm tắt danh mục cho các ngân sách")
    void list_returnsPagedAdminBudgets() {
        Budget b = sampleBudget();
        Pageable pageable = PageRequest.of(0, 10);
        when(budgetRepository.searchByAdmin(USER_ID, false, true, PeriodType.MONTH, pageable))
                .thenReturn(new PageImpl<>(List.of(b), pageable, 1));
        when(categoryHelper.findSummaries(anyCollection()))
                .thenReturn(Map.of(FOOD_CODE, new CateSumaryRes(UUID.randomUUID(), "Ăn uống", FOOD_CODE, "icon_food", "#112233")));

        Page<BudgetAdminRes> result = adminService.list(USER_ID, false, true, PeriodType.MONTH, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        BudgetAdminRes res = result.getContent().get(0);
        assertThat(res.id()).isEqualTo(BUDGET_ID);
        assertThat(res.userId()).isEqualTo(USER_ID);
        assertThat(res.category().name()).isEqualTo("Ăn uống");
    }

    @Test
    @DisplayName("Admin detail: trả về đầy đủ thông tin ngân sách bao gồm userId")
    void detail_found_returnsBudgetAdminRes() {
        Budget b = sampleBudget();
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(b));
        when(categoryHelper.findSummary(FOOD_CODE))
                .thenReturn(Optional.of(new CateSumaryRes(UUID.randomUUID(), "Ăn uống", FOOD_CODE, "icon_food", "#112233")));

        BudgetAdminRes res = adminService.detail(BUDGET_ID);

        assertThat(res.id()).isEqualTo(BUDGET_ID);
        assertThat(res.userId()).isEqualTo(USER_ID);
        assertThat(res.category().name()).isEqualTo("Ăn uống");
        assertThat(res.limitAmount()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("Admin detail: không tìm thấy ngân sách thì ném BusinessException NOT_FOUND")
    void detail_notFound_throwsException() {
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.detail(BUDGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("Admin forceDelete: đặt isActive = false, isDeleted = true và lưu CSDL")
    void forceDelete_updatesFlagsAndSaves() {
        Budget b = sampleBudget();
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(b));

        adminService.forceDelete(BUDGET_ID);

        assertThat(b.getIsActive()).isFalse();
        assertThat(b.getIsDeleted()).isTrue();
        verify(budgetRepository).save(b);
    }

    private static Budget sampleBudget() {
        LocalDate now = LocalDate.now();
        return Budget.builder()
                .id(BUDGET_ID)
                .userId(USER_ID)
                .categoryCode(FOOD_CODE)
                .spent(1_200_000L)
                .limitAmount(3_000_000L)
                .periodType(PeriodType.MONTH)
                .startDate(now.minusDays(5))
                .endDate(now.plusDays(25))
                .autoRenew(true)
                .isActive(true)
                .build();
    }
}
