package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.dto.request.fund.FundReconcileReq;
import com.datn.financeapp.module.group.dto.request.fund.FundKepperUpdateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.module.group.dto.response.fund.GroupFundReconcileRes;
import com.datn.financeapp.module.group.dto.response.fund.FundRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.module.group.entity.Fund;
import com.datn.financeapp.module.group.enums.GTransactionType;
import com.datn.financeapp.module.group.enums.MoneySource;
import com.datn.financeapp.module.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.module.group.events.MemberLeaveEvent;
import com.datn.financeapp.module.group.helper.TransactionHelper;
import com.datn.financeapp.module.group.mapper.FundMapper;
import com.datn.financeapp.module.group.repository.FundRepository;
import com.datn.financeapp.module.group.service.FundService;
import com.datn.financeapp.module.group.service.GTransactionService;
import com.datn.financeapp.module.group.service.MemberService;
import com.datn.financeapp.module.group.validator.GroupPermissionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
    private final TransactionHelper transactionHelper;

    @Transactional
    @Override
    public FundRes create(UUID groupId, UUID operatorId, Instant now) {
        Fund fund = Fund.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .keepperId(operatorId)
                .currentBalance(0L)
                .createdAt(now)
                .build();

        fund = fundRepository.save(fund);

        return fundMapper.toResponse(fund);
    }

    @Override
    @Transactional
    public FundRes updateFundKeepper(UUID operatorId, UUID groupId, FundKepperUpdateReq req) {
        permissionValidator.verifyOwner(groupId, operatorId);

        Fund fund = fundRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND));

        if (req.keepperId() != null) {
            // thủ quỹ mới phải đang là thành viên ACTIVE, nếu không quỹ rơi vào tay người đã rời nhóm
            if (!memberService.allMemberInGroup(groupId, List.of(req.keepperId())))
                throw new BusinessException(ErrorCode.GROUP_FUND_HOLDER_NOT_MEMBER);
            fund.setKeepperId(req.keepperId());
            permissionValidator.evictGroup(groupId);
        }

        fund = fundRepository.save(fund);
        return fundMapper.toResponse(fund);
    }

    //đối chiếu quỹ
    @Override
    @Transactional
    public GroupFundReconcileRes reconcileFund(UUID operatorId, UUID groupId, FundReconcileReq req) {
        // khoá dòng quỹ để hai lần kiểm kê cùng lúc không cùng tính chênh lệch trên một số dư cũ
        Fund fund = fundRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND));

        return executeReconcile(operatorId, fund, req);
    }

    @Override
    @Transactional
    public void adjustBalance(UUID fundId, Long delta) {

        fundRepository.adjustBalance(fundId, delta);

    }

    private GroupFundReconcileRes executeReconcile(UUID operatorId, Fund fund,
                                                   FundReconcileReq req) {

        if (Objects.equals(req.actualBalance(), fund.getCurrentBalance()))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "không thể kiểm kê nếu tiền đang là chính xác");

        // xác định chủ nhóm hoc thử qũy
        permissionValidator.verifyReconciPermission(fund.getGroupId(), operatorId, fund.getKeepperId());

        Instant now = Instant.now();

        // chặn kiểm kê khi còn giao dịch chờ duyệt
        if (gTransactionService.countPendingForGroup(fund.getGroupId()) > 0)
            throw new BusinessException(ErrorCode.GROUP_PENDING_TXN_EXIST);

        // độ chênh lệch
        long difference = req.actualBalance() - fund.getCurrentBalance();
        long amount = Math.abs(difference);

        GTransactionType adjustmentType = difference > 0
                ? GTransactionType.ADJUSTMENT_UP
                : GTransactionType.ADJUSTMENT_DOWN;

        List<GroupTransactionParticipantReq> participantReqs = null;

        if (req.excludedUserIds() != null && !req.excludedUserIds().isEmpty()) {
            List<UUID> allMemberIds = memberService.findIdAllMember(fund.getGroupId());
            List<UUID> includedIds = allMemberIds.stream()
                    .filter(id -> !req.excludedUserIds().contains(id))
                    .toList();

            participantReqs = transactionHelper.splitEvenly(amount, includedIds).stream()
                    .map(p -> new GroupTransactionParticipantReq(p.userId(), p.shareAmount()))
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
                req.reason(),
                participantReqs
        );

        GroupTransactionDetailRes transactionDetail = gTransactionService.create(operatorId, fund.getGroupId(), createReq);

        return new GroupFundReconcileRes(
                fund.getCurrentBalance(),
                req.actualBalance(),
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
        fundRepository.findByGroupIdForUpdate(event.groupId()).ifPresent(fund -> {
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
