package com.datn.financeapp.unitTest.group.service.impl;

import com.datn.financeapp.module.group.service.impl.*;


import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnParticipantReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.events.GroupTxnConfirmedEvent;
import com.datn.financeapp.module.group.helper.GroupTxnHelper;
import com.datn.financeapp.module.group.helper.MemberAuthCache;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.mapper.GroupTxnMapper;
import com.datn.financeapp.module.group.repository.GroupTxnRepository;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupTxnCreateImplTest {

    @Mock
    private GroupTxnRepository groupTxnRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private MemberBalanceService memberBalanceService;
    @Mock
    private GroupTxnHelper groupTxnHelper;
    @Mock
    private GroupTxnMapper groupTxnMapper;
    @Mock
    private MemberAuthCache memberAuthCache;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private GroupTxnCreateImpl service;

    private UUID groupId;
    private UUID ownerId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        authAs(ownerId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList())
        );
    }

    private MemberAuthInfo ownerAuth() {
        return new MemberAuthInfo(groupId, ownerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, ownerId);
    }

    @Test
    @DisplayName("EXPENSE từ quỹ nhóm tạo bởi Owner -> CONFIRMED, lưu và publish event")
    void createExpense_fromFundByOwner_confirmed() {
        when(memberAuthCache.get(groupId, ownerId)).thenReturn(Optional.of(ownerAuth()));
        when(categoryService.findByCodeAndIsDeletedAndIsLeaf("an_uong", false, true))
                .thenReturn(Optional.of(new CateSumaryRes(UUID.randomUUID(), "Ăn uống", "an_uong", "icon", "#color")));
        when(groupTxnRepository.save(any(GroupTxn.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupTxnCreateReq req = new GroupTxnCreateReq(
                GroupTxnType.EXPENSE,
                MoneySource.FUND,
                100_000L,
                Instant.now(),
                "an_uong",
                null,
                "Ăn trưa",
                List.of(new GroupTxnParticipantReq(ownerId, 100_000L))
        );

        service.create(groupId, req);

        verify(groupTxnRepository).save(any(GroupTxn.class));
        verify(memberBalanceService).applyDelta(eq(groupId), any(), any());
        verify(applicationEventPublisher).publishEvent(any(GroupTxnConfirmedEvent.class));
    }

    @Test
    @DisplayName("EXPENSE từ quỹ nhóm nhưng lại có transactorId -> lỗi GROUP_TXN_TRANSACTOR_REQUIRED_NULL")
    void createExpense_fromFundWithTransactor_throwsException() {
        when(memberAuthCache.get(groupId, ownerId)).thenReturn(Optional.of(ownerAuth()));

        GroupTxnCreateReq req = new GroupTxnCreateReq(
                GroupTxnType.EXPENSE,
                MoneySource.FUND,
                100_000L,
                Instant.now(),
                "an_uong",
                ownerId,
                "Ăn trưa",
                List.of(new GroupTxnParticipantReq(ownerId, 100_000L))
        );

        assertThatThrownBy(() -> service.create(groupId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED_NULL.getCode());

        verify(groupTxnRepository, never()).save(any());
    }

    @Test
    @DisplayName("EXPENSE từ tiền túi nhưng thiếu transactorId -> lỗi GROUP_TXN_TRANSACTOR_REQUIRED")
    void createExpense_fromPersonalWithoutTransactor_throwsException() {
        when(memberAuthCache.get(groupId, ownerId)).thenReturn(Optional.of(ownerAuth()));

        GroupTxnCreateReq req = new GroupTxnCreateReq(
                GroupTxnType.EXPENSE,
                MoneySource.PERSONAL,
                100_000L,
                Instant.now(),
                "an_uong",
                null,
                "Ăn trưa",
                List.of(new GroupTxnParticipantReq(ownerId, 100_000L))
        );

        assertThatThrownBy(() -> service.create(groupId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED.getCode());

        verify(groupTxnRepository, never()).save(any());
    }
}
