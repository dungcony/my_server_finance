package com.datn.financeapp.unitTest.group.service.impl;

import com.datn.financeapp.module.group.service.impl.*;


import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.dto.request.fund.FundKepperUpdateReq;
import com.datn.financeapp.module.group.entity.Fund;
import com.datn.financeapp.module.group.enums.GroupStatus;
import com.datn.financeapp.module.group.enums.MemberRole;
import com.datn.financeapp.module.group.enums.MemberStatus;
import com.datn.financeapp.module.group.helper.MemberAuthCache;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.mapper.FundMapper;
import com.datn.financeapp.module.group.repository.FundRepository;
import com.datn.financeapp.module.group.service.GroupTxnCreateService;
import com.datn.financeapp.module.group.service.GroupTxnGetService;
import com.datn.financeapp.module.group.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử {@link FundServiceImpl#updateFundKeepper}: chủ nhóm bàn giao thủ quỹ.
 */
@ExtendWith(MockitoExtension.class)
class FundServiceImplTest {

    @Mock
    private FundRepository fundRepository;
    @Mock
    private GroupTxnGetService groupTxnGetService;
    @Mock
    private GroupTxnCreateService groupTxnCreateService;
    @Mock
    private MemberAuthCache authCache;
    @Mock
    private FundMapper fundMapper;
    @Mock
    private MemberService memberService;

    private FundServiceImpl service;

    private UUID groupId;
    private UUID ownerId;
    private UUID newKeeperId;
    private Fund fund;

    @BeforeEach
    void setUp() {
        service = new FundServiceImpl(fundRepository, groupTxnGetService, groupTxnCreateService, authCache, fundMapper, memberService);
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        newKeeperId = UUID.randomUUID();
        fund = Fund.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .keepperId(ownerId)
                .currentBalance(0L)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ownerId.toString(), null, Collections.emptyList())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Đổi thủ quỹ sang thành viên ACTIVE thì cập nhật keepperId và lưu lại")
    void updateFundKeepper_ActiveMember_UpdatesKeeper() {
        when(authCache.get(groupId, ownerId)).thenReturn(Optional.of(
                new MemberAuthInfo(groupId, ownerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, ownerId)
        ));
        when(fundRepository.findByGroupIdForUpdate(groupId)).thenReturn(Optional.of(fund));
        when(memberService.allMemberInGroup(groupId, List.of(newKeeperId))).thenReturn(true);
        when(fundRepository.save(fund)).thenReturn(fund);

        service.updateFundKeepper(groupId, new FundKepperUpdateReq(newKeeperId));

        assertThat(fund.getKeepperId()).isEqualTo(newKeeperId);
        verify(fundRepository).save(fund);
    }

    @Test
    @DisplayName("Đổi thủ quỹ sang người không còn ACTIVE trong nhóm thì lỗi GROUP_FUND_HOLDER_NOT_MEMBER và giữ nguyên thủ quỹ cũ")
    void updateFundKeepper_NotActiveMember_Rejected() {
        when(authCache.get(groupId, ownerId)).thenReturn(Optional.of(
                new MemberAuthInfo(groupId, ownerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, ownerId)
        ));
        when(fundRepository.findByGroupIdForUpdate(groupId)).thenReturn(Optional.of(fund));
        when(memberService.allMemberInGroup(groupId, List.of(newKeeperId))).thenReturn(false);

        assertThatThrownBy(() -> service.updateFundKeepper(groupId, new FundKepperUpdateReq(newKeeperId)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_FUND_HOLDER_NOT_MEMBER.getCode());

        assertThat(fund.getKeepperId()).isEqualTo(ownerId);
        verify(fundRepository, never()).save(any(Fund.class));
    }

    @Test
    @DisplayName("Người không phải chủ nhóm đổi thủ quỹ thì bị chặn ngay từ bước kiểm tra quyền")
    void updateFundKeepper_NotOwner_Forbidden() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(newKeeperId.toString(), null, Collections.emptyList())
        );
        when(authCache.get(groupId, newKeeperId)).thenReturn(Optional.of(
                new MemberAuthInfo(groupId, newKeeperId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.MEMBER, ownerId)
        ));

        assertThatThrownBy(() -> service.updateFundKeepper(groupId, new FundKepperUpdateReq(ownerId)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());

        verify(fundRepository, never()).save(any(Fund.class));
    }
}