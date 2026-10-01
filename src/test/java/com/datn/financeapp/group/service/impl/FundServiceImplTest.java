package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.fund.FundKepperUpdateReq;
import com.datn.financeapp.group.entity.Fund;
import com.datn.financeapp.group.mapper.FundMapper;
import com.datn.financeapp.group.repository.FundRepository;
import com.datn.financeapp.group.service.GTransactionService;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private GTransactionService gTransactionService;
    @Mock
    private GroupPermissionValidator permissionValidator;
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
        service = new FundServiceImpl(fundRepository, gTransactionService, permissionValidator, fundMapper,
                memberService);
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        newKeeperId = UUID.randomUUID();
        fund = Fund.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .keepperId(ownerId)
                .currentBalance(0L)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Đổi thủ quỹ sang thành viên ACTIVE thì cập nhật keepperId và lưu lại")
    void updateFundKeepper_ActiveMember_UpdatesKeeper() {
        when(fundRepository.findByGroupId(groupId)).thenReturn(Optional.of(fund));
        when(memberService.allMemberInGroup(groupId, List.of(newKeeperId))).thenReturn(true);
        when(fundRepository.save(fund)).thenReturn(fund);

        service.updateFundKeepper(ownerId, groupId, new FundKepperUpdateReq(newKeeperId));

        assertThat(fund.getKeepperId()).isEqualTo(newKeeperId);
        verify(fundRepository).save(fund);
    }

    @Test
    @DisplayName("Đổi thủ quỹ sang người không còn ACTIVE trong nhóm thì lỗi HOLDER_NOT_MEMBER và giữ nguyên thủ quỹ cũ")
    void updateFundKeepper_NotActiveMember_Rejected() {
        when(fundRepository.findByGroupId(groupId)).thenReturn(Optional.of(fund));
        when(memberService.allMemberInGroup(groupId, List.of(newKeeperId))).thenReturn(false);

        assertThatThrownBy(() -> service.updateFundKeepper(ownerId, groupId, new FundKepperUpdateReq(newKeeperId)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.HOLDER_NOT_MEMBER.getCode());

        assertThat(fund.getKeepperId()).isEqualTo(ownerId);
        verify(fundRepository, never()).save(any(Fund.class));
    }

    @Test
    @DisplayName("Người không phải chủ nhóm đổi thủ quỹ thì bị chặn ngay từ bước kiểm tra quyền")
    void updateFundKeepper_NotOwner_Forbidden() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED))
                .when(permissionValidator).verifyOwner(groupId, newKeeperId);

        assertThatThrownBy(() -> service.updateFundKeepper(newKeeperId, groupId, new FundKepperUpdateReq(ownerId)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN_OWNER_REQUIRED.getCode());

        verify(fundRepository, never()).save(any(Fund.class));
    }
}
