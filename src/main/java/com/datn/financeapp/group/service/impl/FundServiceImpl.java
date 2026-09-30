package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.fund.GroupFundReconcileReq;
import com.datn.financeapp.group.dto.request.fund.GroupFundUpdateReq;
import com.datn.financeapp.group.dto.response.fund.GroupFundReconcileRes;
import com.datn.financeapp.group.dto.response.fund.GroupFundRes;
import com.datn.financeapp.group.entity.Fund;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.group.events.MemberLeaveEvent;
import com.datn.financeapp.group.mapper.FundMapper;
import com.datn.financeapp.group.repository.FundRepository;
import com.datn.financeapp.group.service.FundService;
import com.datn.financeapp.group.service.GTransactionService;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FundServiceImpl implements FundService {

    private final FundRepository fundRepository;
    private final GTransactionService gTransactionService;
    private final GroupPermissionValidator permissionValidator;
    private final FundMapper fundMapper;
    private final MemberService memberService;

    @Override
    @Transactional
    public GroupFundRes addFund(UUID groupId, UUID heldByUserId, Instant createdAt) {
        Fund fund = Fund.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .heldByUserId(heldByUserId)
                .currentBalance(0L)
                .createdAt(createdAt)
                .build();
        fund = fundRepository.save(fund);
        return fundMapper.toResponse(fund);
    }

    @Override
    public GroupFundRes getFund(UUID operatorId, UUID groupId) {
        permissionValidator.verifyActiveMemberInGroupActive(groupId, operatorId);
        return getFund(groupId);
    }

    @Override
    public GroupFundRes getFund(UUID groupId) {
        Fund fund = fundRepository.findByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND));
        return fundMapper.toResponse(fund);
    }

    @Override
    @Transactional
    public GroupFundRes updateFund(UUID operatorId, UUID groupId, GroupFundUpdateReq req) {
        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        Fund fund = fundRepository.findByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND));

        if (req.heldByUserId() != null) {
            permissionValidator.verifyActiveMemberInGroupActive(groupId, req.heldByUserId());
            fund.setHeldByUserId(req.heldByUserId());
        }

        fund = fundRepository.save(fund);
        return fundMapper.toResponse(fund);
    }

    @Override
    @Transactional
    public GroupFundReconcileRes reconcileFund(UUID operatorId, UUID groupId, GroupFundReconcileReq req) {
        Fund fund = fundRepository.findByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND));

        return executeReconcile(operatorId, groupId, fund, req);
    }

    @Override
    @Transactional
    public void adjustBalance(UUID fundId, Long delta) {
        fundRepository.adjustBalance(fundId, delta);
    }

    private GroupFundReconcileRes executeReconcile(UUID operatorId, UUID groupId, Fund fund,
                                                   GroupFundReconcileReq req) {
        permissionValidator.verifyOwnerOrTreasurer(groupId, operatorId, fund.getHeldByUserId());

        long previousBalance = fund.getCurrentBalance();
        long actualBalance = req.actualBalance();
        long difference = actualBalance - previousBalance;

        if (difference == 0) {
            return new GroupFundReconcileRes(
                    fund.getId(),
                    previousBalance,
                    actualBalance,
                    0L,
                    null,
                    null);
        }

        TransactionType adjustmentType = difference > 0
                ? TransactionType.ADJUSTMENT_UP
                : TransactionType.ADJUSTMENT_DOWN;
        long amount = Math.abs(difference);

        Instant now = Instant.now();
        List<com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq> participantReqs = null;
        if (req.excludedUserIds() != null && !req.excludedUserIds().isEmpty()) {
            List<UUID> allMemberIds = memberService.findIdAllMember(groupId);
            participantReqs = allMemberIds.stream()
                    .filter(id -> !req.excludedUserIds().contains(id))
                    .map(id -> new com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq(id, null))
                    .toList();
        }

        com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq createReq = new com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq(
                adjustmentType,
                com.datn.financeapp.group.enums.MoneySource.FUND,
                amount,
                now,
                null,
                null,
                fund.getHeldByUserId(),
                req.note(),
                participantReqs
        );

        com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes transactionDetail = gTransactionService.create(operatorId, groupId, createReq);
        UUID transactionId = transactionDetail.id();

        return new GroupFundReconcileRes(
                fund.getId(),
                previousBalance,
                actualBalance,
                difference,
                adjustmentType,
                transactionId);
    }

    /**
     * Bàn giao quỹ về chủ nhóm nếu người rời/bị xóa là thủ quỹ hiện tại.
     */
    @EventListener
    @Transactional
    public void onMemberLeave(MemberLeaveEvent event) {
        fundRepository.findByGroupId(event.groupId()).ifPresent(fund -> {
            if (fund.getHeldByUserId().equals(event.memberId())) {
                fund.setHeldByUserId(event.ownerId());
                fundRepository.save(fund);
            }
        });
    }

    @EventListener
    @Transactional
    public void onFundBalanceAdjust(FundBalanceChangedEvent event) {
        fundRepository.adjustBalanceByGroupId(event.groupId(), event.delta());
    }

}
