package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.group.dto.request.fund.FundReconcileReq;
import com.datn.financeapp.module.group.dto.request.fund.FundKepperUpdateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnParticipantReq;
import com.datn.financeapp.module.group.dto.response.fund.GroupFundReconcileRes;
import com.datn.financeapp.module.group.dto.response.fund.FundRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.entity.Fund;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import com.datn.financeapp.module.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.module.group.events.MemberLeaveEvent;
import com.datn.financeapp.module.group.helper.GroupTxnHelper;
import com.datn.financeapp.module.group.helper.MemberAuthCache;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.mapper.FundMapper;
import com.datn.financeapp.module.group.repository.FundRepository;
import com.datn.financeapp.module.group.service.FundService;
import com.datn.financeapp.module.group.service.GroupTxnCreateService;
import com.datn.financeapp.module.group.service.GroupTxnGetService;
import com.datn.financeapp.module.group.service.MemberService;
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
    private final GroupTxnGetService groupTxnGetService;
    private final GroupTxnCreateService groupTxnCreateService;
    private final MemberAuthCache authCache;
    private final FundMapper fundMapper;
    private final MemberService memberService;

    @Transactional
    @Override
    public FundRes create(UUID groupId, UUID operatorId) {
        Fund fund = Fund.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .keepperId(operatorId)
                .currentBalance(0L)
                .build();

        fund = fundRepository.save(fund);

        return fundMapper.toResponse(fund);
    }

    @Override
    @Transactional
    public FundRes updateFundKeepper(UUID groupId, FundKepperUpdateReq req) {
        UUID operatorId = SecurityContextUtil.currentUserId();

        authCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::isOwner)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        Fund fund = fundRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND));

        if (req.keepperId() != null) {
            // thủ quỹ mới phải đang là thành viên ACTIVE, nếu không quỹ rơi vào tay người đã rời nhóm
            if (!memberService.allMemberInGroup(groupId, List.of(req.keepperId())))
                throw new BusinessException(ErrorCode.GROUP_FUND_HOLDER_NOT_MEMBER);
            fund.setKeepperId(req.keepperId());
            authCache.evictGroup(groupId);
        }

        fund = fundRepository.save(fund);
        return fundMapper.toResponse(fund);
    }

    //đối chiếu quỹ
    @Override
    @Transactional
    public GroupFundReconcileRes reconcileFund(UUID groupId, FundReconcileReq req) {

        Instant now = Instant.now();
        UUID operatorId = SecurityContextUtil.currentUserId();

        authCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::isTreasurer)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        // khoá dòng quỹ để hai lần kiểm kê cùng lúc không cùng tính chênh lệch trên một số dư cũ
        Fund fund = fundRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND));

        if (Objects.equals(req.actualBalance(), fund.getCurrentBalance()))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "không thể kiểm kê nếu tiền đang là chính xác");

        return executeReconcile(fund, req, now);
    }

    @Override
    @Transactional
    public void adjustBalance(UUID fundId, Long delta) {
        fundRepository.adjustBalance(fundId, delta);
    }

    private GroupFundReconcileRes executeReconcile(Fund fund, FundReconcileReq req, Instant now) {

        // chặn kiểm kê khi còn giao dịch chờ duyệt hoặc chờ người chi trả đồng ý
        if (groupTxnGetService.countUnresolvedForGroup(fund.getGroupId()) > 0)
            throw new BusinessException(ErrorCode.GROUP_PENDING_TXN_EXIST);

        // độ chênh lệch
        long difference = req.actualBalance() - fund.getCurrentBalance();
        long amount = Math.abs(difference);

        GroupTxnType adjustmentType = difference > 0
                ? GroupTxnType.ADJUSTMENT_UP
                : GroupTxnType.ADJUSTMENT_DOWN;

        // chia đều chênh lệch cho thành viên nhóm, trừ những người bị loại
        List<UUID> excludedIds = req.excludedUserIds() != null ? req.excludedUserIds() : List.of();
        List<UUID> includedIds = memberService.findIdAllMember(fund.getGroupId()).stream()
                .filter(id -> !excludedIds.contains(id))
                .toList();

        List<GroupTxnParticipantReq> participantReqs = GroupTxnHelper.splitEvenly(amount, includedIds).stream()
                .map(p -> new GroupTxnParticipantReq(p.userId(), p.shareAmount()))
                .toList();

        // kiểm kê dùng tiền quỹ nên không có người chi (transactorId = null)
        GroupTxnCreateReq createReq = new GroupTxnCreateReq(
                adjustmentType,
                MoneySource.FUND,
                amount,
                now,
                null,
                null,
                req.reason(),
                participantReqs
        );

        GroupTxnDetailRes transactionDetail = groupTxnCreateService.create(fund.getGroupId(), createReq);

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
            // Chỉ chạy khi người rời nhóm CHÍNH LÀ THỦ QUỸ (người giữ quỹ)
            if (fund.getKeepperId().equals(event.memberId())) {
                fund.setKeepperId(event.ownerId());
                fundRepository.save(fund);
                authCache.evictMember(event.groupId(), event.ownerId());
            }
        });
    }

    @EventListener
    @Transactional
    public void onFundBalanceAdjust(FundBalanceChangedEvent event) {
        fundRepository.adjustBalanceByGroupId(event.groupId(), event.delta());
    }

}
