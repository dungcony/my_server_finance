package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.category.helper.CateCache;
import com.datn.financeapp.module.group.dto.request.transaction.*;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.entity.GroupTxnParticipant;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import com.datn.financeapp.module.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.module.group.events.GroupTxnConfirmedEvent;
import com.datn.financeapp.module.group.helper.*;
import com.datn.financeapp.module.group.mapper.GroupTxnMapper;
import com.datn.financeapp.module.group.repository.GroupTxnRepository;
import com.datn.financeapp.module.group.service.GroupTxnUpdateService;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GroupTxnUpdateImpl implements GroupTxnUpdateService {

    private final GroupTxnRepository groupTxnRepository;
    private final GroupTxnMapper groupTransactionMapper;

    private final MemberAuthCache memberAuthCache;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CateCache cateCache;

    private final MemberBalanceService balanceService;

    @Override
    public GroupTxnDetailRes update(UUID groupId, GroupTxnUpdateReq req) {
        UUID operatorId = SecurityContextUtil.currentUserId();
        GroupTxn txn = groupTxnRepository.findByIdAndGroupIdAndDeletedAtIsNull(req.txnId(), groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

        // bỏ qua giao dịch rejected
        if (txn.getStatus() == GroupTxnStatus.REJECTED)
            throw new BusinessException(ErrorCode.GROUP_TXN_IS_REJECT);

        // Chỉ thủ quỹ được đều chỉnh
        MemberAuthInfo auth = memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupActive)
                .filter(m -> m.isEditGroupTransaction(txn))// bỏ qua nếu k có quyền chỉnh sửa
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));


        MemberBalances before = BalanceCalculator.effectOf(txn);   // chụp trước khi đổi
        UUID oldTransactorId = txn.getTransactorId();
        long oldDelta = GroupTxnHelper.calculateDelta(txn);

        if (req.type() != null || req.moneySource() != null || req.transactorId() != null || req.categoryCode() != null || req.participants() != null)
            validTypeAndMoneySources(txn, req.type(), req.moneySource(), req.transactorId(), req.categoryCode(), req.participants(), auth, operatorId);

        if (req.amount() != null) {
            if (txn.getType() != GroupTxnType.CONTRIBUTION && req.participants() == null)
                throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANTS_REQUIRED);
            txn.setAmount(req.amount());
        }
        if (req.participants() != null)
            txn.setParticipants(GroupTxnHelper.getParticipants(req.participants()));

        if (req.transactorId() != null)
            txn.setTransactorId(req.transactorId());

        if (req.categoryCode() != null) {
            txn.setCategoryId(cateCache.findId(req.categoryCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND)));
        }

        if (req.note() != null)
            txn.setNote(req.note());

        if (req.occurredAt() != null)
            txn.setOccurredAt(req.occurredAt());

        if (txn.getStatus() == GroupTxnStatus.CONFIRMED) {
            // tính lại tiền
            balanceService.applyDelta(txn.getGroupId(), before, BalanceCalculator.effectOf(txn));
        }

        long fundDelta = GroupTxnHelper.calculateDelta(txn) - oldDelta;

        if (fundDelta != 0)
            applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(txn.getGroupId(), fundDelta)); // quỹ

        return null;
    }

    @Override
    public GroupTxnDetailRes status(UUID groupId, GroupTxnUpdateStatusReq req) {
        UUID operatorId = SecurityContextUtil.currentUserId();
        Instant now = Instant.now();

        memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupActive) //phải ở trong group đang active
                .filter(a -> !a.isMember()) // và phải là owner hoặc thủ quỹ mới có quyền
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        // chỉ cập nhật được trạng thái những giao dịch pending
        GroupTxn transaction = groupTxnRepository.findByIdAndStatusAndDeletedAtIsNull(req.txnId(), GroupTxnStatus.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TXN_NOT_FOUD));


        transaction.setReviewedBy(operatorId);
        transaction.setReviewedAt(now);
        transaction.setStatus(req.status());

        switch (req.status()) {
            case CONFIRMED -> {
                // tính chênh lệch vào bảng tổng
                balanceService.applyDelta(groupId, new MemberBalances(), BalanceCalculator.effectOf(transaction));
                // tính lại quỹ
                applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, GroupTxnHelper.calculateDelta(transaction)));

                // hỏi những người liên quan có muốn ghi vào sổ cá nhân không (gửi sau khi commit)
                applicationEventPublisher.publishEvent(GroupTxnConfirmedEvent.of(transaction));
            }
            case REJECTED -> transaction.setStatus(GroupTxnStatus.REJECTED);
        }

        return groupTransactionMapper.toDetailResponse(transaction);
    }

    @Override
    public int statuses(UUID groupId, BatchGroupTxnUpdateStatusReq req) {
        UUID operatorId = SecurityContextUtil.currentUserId();
        Instant now = Instant.now();
        memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupActive) //phải ở trong group đang active
                .filter(a -> !a.isMember()) // và phải là owner hoặc thủ quỹ mới có quyền
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        List<GroupTxn> txns = groupTxnRepository.findByIdInAndStatusAndDeletedAtIsNull(req.txnIds(), GroupTxnStatus.PENDING);

        if (txns.size() != req.txnIds().size())
            throw new BusinessException(ErrorCode.GROUP_TXN_NOT_FOUD);


        long totalDelta = 0L;
        MemberBalances totalEffect = new MemberBalances();

        for (var t : txns) {
            t.setReviewedAt(now);
            t.setStatus(req.status());
            t.setReviewedBy(operatorId);

            totalDelta += GroupTxnHelper.calculateDelta(t);
            totalEffect = totalEffect.plus(BalanceCalculator.effectOf(t));

            // hỏi những người liên quan có muốn ghi vào sổ cá nhân không (gửi sau khi commit)
            applicationEventPublisher.publishEvent(GroupTxnConfirmedEvent.of(t));
        }

        balanceService.applyDelta(groupId, new MemberBalances(), totalEffect);

        // cập nhật số dư quỹ 1 lần duy nhất cho cả lô
        applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, totalDelta));

        return txns.size();
    }

    //-------------------------------------- PRIVATE -----------------------------------------//


    private void validTypeAndMoneySources(GroupTxn txn,
                                          GroupTxnType newType, MoneySource newSource, UUID newTransactorId, String newCateCode, List<GroupTxnParticipantReq> newParticipants,
                                          MemberAuthInfo auth, UUID operatorId) {

        GroupTxnType type = newType != null ? newType : txn.getType();
        MoneySource source = newSource != null ? newSource : txn.getMoneySource();

        // client gửi thì lấy của client; không gửi thì FUND/REFUND là null, còn lại giữ người cũ
        UUID transactorId = newTransactorId != null ? newTransactorId
                : (source == MoneySource.FUND || type == GroupTxnType.REFUND) ? null : txn.getTransactorId();

        List<GroupTxnParticipant> participants = newParticipants != null
                ? GroupTxnHelper.getParticipants(newParticipants) : txn.getParticipants();

        boolean hasCategory = (newCateCode != null && !newCateCode.isBlank())
                || txn.getCategoryId() != null;

        switch (type) {
            case CONTRIBUTION -> {
                if (source == MoneySource.FUND)
                    throw new BusinessException(ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID);
                if (transactorId == null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED);
                if (participants.size() > 1)
                    throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANTS_NOT_ALLOWED);
            }
            case EXPENSE -> {
                if (source == MoneySource.PERSONAL && transactorId == null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED);
                if (source == MoneySource.FUND && transactorId != null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED_NULL);
                if (participants.isEmpty())
                    throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANT_REQUIRED);
                if (!hasCategory)
                    throw new BusinessException(ErrorCode.GROUP_TXN_CATEGORY_REQUIRED);
            }
            case REFUND -> {
                if (!auth.isTreasurer())
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                if (source == MoneySource.PERSONAL)
                    throw new BusinessException(ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID);
                if (transactorId != null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED_NULL);
                if (participants.isEmpty())
                    throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANT_REQUIRED);
                // kiểm tra hạn mức hoàn ở đây
            }
            default -> throw new BusinessException(ErrorCode.GROUP_TXN_ADJUSTMENT_NOT_EDITABLE);
        }

        // thành viên thường chỉ đứng tên chính mình khi dùng tiền túi
        if (auth.isMember() && transactorId != null && !operatorId.equals(transactorId))
            throw new BusinessException(ErrorCode.GROUP_TXN_EDIT_FORBIDDEN);

        txn.setType(type);
        txn.setMoneySource(source);
        txn.setTransactorId(transactorId);

    }

}
