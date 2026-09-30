package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.fund.FundReconcileReq;
import com.datn.financeapp.group.dto.request.fund.FundKepperUpdateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.group.dto.response.fund.GroupFundReconcileRes;
import com.datn.financeapp.group.dto.response.fund.GroupFundRes;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.entity.Fund;
import com.datn.financeapp.group.enums.MoneySource;
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
    public GroupFundRes updateFundKeepper(UUID operatorId, UUID groupId, FundKepperUpdateReq req) {
        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        Fund fund = fundRepository.findByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND));

        if (req.keepperId() != null) {
            permissionValidator.verifyActiveMemberInGroupActive(groupId, req.keepperId());
            fund.setKeepperId(req.keepperId());
        }

        fund = fundRepository.save(fund);
        return fundMapper.toResponse(fund);
    }

    //đối chiếu quỹ
    @Override
    @Transactional
    public GroupFundReconcileRes reconcileFund(UUID operatorId, UUID groupId, FundReconcileReq req) {
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
                                                   FundReconcileReq req) {
        permissionValidator.verifyOwnerOrTreasurer(groupId, operatorId, fund.getKeepperId());

        long previousBalance = fund.getCurrentBalance();
        long actualBalance = req.actualBalance();
        long difference = actualBalance - previousBalance;

        if (difference == 0) {
            return new GroupFundReconcileRes(
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
        List<GroupTransactionParticipantReq> participantReqs = null;
        if (req.excludedUserIds() != null && !req.excludedUserIds().isEmpty()) {
            List<UUID> allMemberIds = memberService.findIdAllMember(groupId);
            participantReqs = allMemberIds.stream()
                    .filter(id -> !req.excludedUserIds().contains(id))
                    .map(id -> new GroupTransactionParticipantReq(id, null))
                    .toList();
        }

        GroupTransactionCreateReq createReq = new GroupTransactionCreateReq(
                adjustmentType,
                MoneySource.FUND,
                amount,
                now,
                null,
                null,
                fund.getKeepperId(),
                req.note(),
                participantReqs
        );

        GroupTransactionDetailRes transactionDetail = gTransactionService.create(operatorId, groupId, createReq);

        return new GroupFundReconcileRes(
                previousBalance,
                actualBalance,
                difference,
                adjustmentType,
                transactionDetail.id());
    }

    /**
     * Bàn giao quỹ về chủ nhóm nếu người rời/bị xóa là thủ quỹ hiện tại.
     */
    @EventListener
    @Transactional
    public void onMemberLeave(MemberLeaveEvent event) {
        fundRepository.findByGroupId(event.groupId()).ifPresent(fund -> {
            if (fund.getKeepperId().equals(event.memberId())) {
                fund.setKeepperId(event.ownerId());
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
