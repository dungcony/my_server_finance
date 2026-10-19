package com.datn.financeapp.unitTest.goal.service.impl;

import com.datn.financeapp.module.goal.dto.request.GoalFilterRequest;
import com.datn.financeapp.module.goal.dto.response.GoalListItemResponse;
import com.datn.financeapp.module.goal.entity.SavingsGoal;
import com.datn.financeapp.module.goal.helper.GoalSpecs;
import com.datn.financeapp.module.goal.mapper.GoalMapper;
import com.datn.financeapp.module.goal.repository.GoalContributionRepository;
import com.datn.financeapp.module.goal.repository.SavingsGoalRepository;
import com.datn.financeapp.module.goal.service.impl.GoalServiceImpl;
import com.datn.financeapp.module.icon.mapper.IconMapper;
import com.datn.financeapp.module.icon.repository.IconRepository;
import com.datn.financeapp.module.transaction.service.TxnBehavierService;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletBehavierService;
import com.datn.financeapp.module.wallet.service.WalletService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoalServiceImplAdminTest {

    @Mock
    private SavingsGoalRepository savingsGoalRepository;
    @Mock
    private GoalContributionRepository goalContributionRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private WalletBehavierService walletBehavierService;
    @Mock
    private TxnBehavierService txnBehavierService;
    @Mock
    private IconRepository iconRepository;
    @Mock
    private IconMapper iconMapper;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private final GoalMapper goalMapper = Mappers.getMapper(GoalMapper.class);

    private GoalServiceImpl goalService;

    @BeforeEach
    void setUp() {
        goalService = new GoalServiceImpl(
                savingsGoalRepository,
                goalContributionRepository,
                walletRepository,
                walletService,
                walletBehavierService,
                txnBehavierService,
                iconRepository,
                iconMapper,
                goalMapper,
                applicationEventPublisher
        );
    }

    @Test
    @DisplayName("adminList: fallback sang createdAt DESC khi pageable unsorted")
    @SuppressWarnings("unchecked")
    void adminList_UnsortedPageable_FallsBackToCreatedAtDesc() {
        UUID userId = UUID.randomUUID();
        GoalFilterRequest filter = new GoalFilterRequest(userId, "in_progress", "house", null, null);
        Pageable pageable = PageRequest.of(0, 10); // unsorted

        SavingsGoal goal = SavingsGoal.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("house")
                .targetAmount(100_000_000L)
                .savedAmount(10_000_000L)
                .status("in_progress")
                .build();

        when(savingsGoalRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(goal), pageable, 1));
        when(savingsGoalRepository.findSavedAmountNative(goal.getId())).thenReturn(Optional.of(10_000_000L));
        when(savingsGoalRepository.findStatusNative(goal.getId())).thenReturn(Optional.of("in_progress"));

        Page<GoalListItemResponse> result = goalService.adminList(filter, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(savingsGoalRepository).findAll(any(Specification.class), captor.capture());

        Pageable effective = captor.getValue();
        assertThat(effective.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(effective.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("adminList: giữ nguyên sort của caller khi pageable đã có sort")
    @SuppressWarnings("unchecked")
    void adminList_WithSort_PreservesSort() {
        UUID userId = UUID.randomUUID();
        GoalFilterRequest filter = new GoalFilterRequest(userId, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));

        when(savingsGoalRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        goalService.adminList(filter, pageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(savingsGoalRepository).findAll(any(Specification.class), captor.capture());

        Pageable effective = captor.getValue();
        assertThat(effective.getSort().getOrderFor("name")).isNotNull();
        assertThat(effective.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("GoalSpecs: filter tạo specification hợp lệ không throw exception khi tất cả fields null hoặc rỗng")
    void goalSpecs_EmptyFilter_DoesNotThrow() {
        Specification<SavingsGoal> spec1 = GoalSpecs.filter(null);
        assertThat(spec1).isNotNull();

        Specification<SavingsGoal> spec2 = GoalSpecs.filter(new GoalFilterRequest(null, "", "   ", null, null));
        assertThat(spec2).isNotNull();
    }
}
