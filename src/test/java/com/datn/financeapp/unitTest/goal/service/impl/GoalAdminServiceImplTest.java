package com.datn.financeapp.unitTest.goal.service.impl;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.goal.dto.request.SavingGoalFilterReq;
import com.datn.financeapp.module.goal.dto.response.SavingGoalListRes;
import com.datn.financeapp.module.goal.entity.SavingGoal;
import com.datn.financeapp.module.goal.enums.SavingGoalStatus;
import com.datn.financeapp.module.goal.helper.ContributionHelper;
import com.datn.financeapp.module.goal.helper.GoalSpecs;
import com.datn.financeapp.module.goal.helper.SavingGoalHelper;
import com.datn.financeapp.module.goal.mapper.SavingGoalMapper;
import com.datn.financeapp.module.goal.repository.SavingsGoalRepository;
import com.datn.financeapp.module.goal.service.impl.SavingBehavierImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoalAdminServiceImplTest {

    @Mock
    private SavingsGoalRepository savingsGoalRepository;
    @Mock
    private ContributionHelper contributionHelper;
    @Mock
    private SavingGoalHelper savingGoalHelper;
    @Mock
    private SavingGoalMapper savingGoalMapper;

    private SavingBehavierImpl goalBehavierService;

    @BeforeEach
    void setUp() {
        goalBehavierService = new SavingBehavierImpl(
                savingsGoalRepository,
                contributionHelper,
                savingGoalHelper,
                savingGoalMapper
        );
    }

    @Test
    @DisplayName("gets (admin): fallback sang createdAt DESC khi pageable unsorted")
    @SuppressWarnings("unchecked")
    void gets_AdminUnsortedPageable_FallsBackToCreatedAtDesc() {
        UUID userId = UUID.randomUUID();
        SavingGoalFilterReq filter = new SavingGoalFilterReq(userId, SavingGoalStatus.IN_PROGRESS, "house", null, null);
        Pageable pageable = PageRequest.of(0, 10);

        SavingGoal goal = SavingGoal.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("house")
                .targetAmount(100_000_000L)
                .savedAmount(10_000_000L)
                .status(SavingGoalStatus.IN_PROGRESS)
                .build();

        Page<SavingGoal> page = new PageImpl<>(List.of(goal), pageable, 1);
        when(savingsGoalRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        SavingGoalListRes.SavingGoalSummayRes summary = new SavingGoalListRes.SavingGoalSummayRes(
                goal.getId(), goal.getName(), goal.getStatus(), goal.getTargetAmount(), goal.getSavedAmount(), null, null);
        SavingGoalListRes expectedRes = new SavingGoalListRes(List.of(summary), new PageMeta(0, 10, 1, 1));
        when(savingGoalMapper.toListRes(page)).thenReturn(expectedRes);

        SavingGoalListRes result = goalBehavierService.gets(filter, pageable, true);

        assertThat(result).isNotNull();
        assertThat(result.goals()).hasSize(1);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(savingsGoalRepository).findAll(any(Specification.class), captor.capture());

        Pageable effective = captor.getValue();
        assertThat(effective.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(effective.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("gets (admin): giữ nguyên sort của caller khi pageable đã có sort")
    @SuppressWarnings("unchecked")
    void gets_AdminWithSort_PreservesSort() {
        UUID userId = UUID.randomUUID();
        SavingGoalFilterReq filter = new SavingGoalFilterReq(userId, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));

        Page<SavingGoal> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(savingsGoalRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);
        when(savingGoalMapper.toListRes(emptyPage))
                .thenReturn(new SavingGoalListRes(Collections.emptyList(), new PageMeta(0, 20, 0, 0)));

        goalBehavierService.gets(filter, pageable, true);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(savingsGoalRepository).findAll(any(Specification.class), captor.capture());

        Pageable effective = captor.getValue();
        assertThat(effective.getSort().getOrderFor("name")).isNotNull();
        assertThat(effective.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("GoalSpecs: filter tạo specification hợp lệ không throw exception khi tất cả fields null hoặc rỗng")
    void goalSpecs_EmptyFilter_DoesNotThrow() {
        Specification<SavingGoal> spec1 = GoalSpecs.filter(null);
        assertThat(spec1).isNotNull();

        Specification<SavingGoal> spec2 = GoalSpecs.filter(new SavingGoalFilterReq(null, null, "   ", null, null));
        assertThat(spec2).isNotNull();
    }
}
